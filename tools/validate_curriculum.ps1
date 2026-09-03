[CmdletBinding()]
param(
    [string]$CatalogPath = "generated/curriculum_v2.json",
    [string]$BaselinePath = "street_gymnastic_programs_levels.json",
    [string]$AppSourcePath = "app/src/main",
    [string[]]$AuthoredSourcePaths = @(
        "curriculum/l1_tail.json",
        "curriculum/l2_l3_tails.json",
        "curriculum/l3_extension.json",
        "curriculum/l4_l5.json",
        "curriculum/l6.json"
    )
)

$ErrorActionPreference = "Stop"
$expectedBaselineSha256 = "AC8AAF41F477050EDDF9387DF1CD214FCBE3A29608AE4D5AE1E0C34D9F5EDA3D"
$errors = New-Object System.Collections.ArrayList
$warnings = New-Object System.Collections.ArrayList
$allowedLateralRegressionPairs = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
foreach ($pair in @(
    "active_hang->dead_hang",
    "bent_dragon_negative->lying_leg_raise",
    "cossack_squat->squat",
    "hollow_arch_swing->hollow_hold",
    "incline_pushup->wall_pushup",
    "ninety_ninety_hip_switch->hip_circles",
    "pseudo_planche_pushup->planche_lean",
    "scapular_pull->dead_hang",
    "side_plank->dead_bug",
    "side_plank->plank",
    "tuck_lsit->straight_bar_support",
    "wall_slide->shoulder_cars"
)) {
    [void]$allowedLateralRegressionPairs.Add($pair)
}

function Add-Error([string]$Message) {
    [void]$script:errors.Add($Message)
}

function Add-Warning([string]$Message) {
    [void]$script:warnings.Add($Message)
}

function Has-Text($Value) {
    return $null -ne $Value -and -not [string]::IsNullOrWhiteSpace([string]$Value)
}

function Has-Property($Value, [string]$Name) {
    if ($null -eq $Value) { return $false }
    return $null -ne $Value.PSObject.Properties[$Name]
}

function Get-PropertyValue($Value, [string]$Name) {
    if (-not (Has-Property $Value $Name)) { return $null }
    return $Value.PSObject.Properties[$Name].Value
}

function Test-DormantVideoMetadataKey([string]$Name) {
    $normalized = [regex]::Replace($Name, '[_\-\s]', '').ToLowerInvariant()
    return $normalized -in @(
        "video",
        "videoid",
        "videourl",
        "videoprovider",
        "url",
        "provider",
        "vimeo",
        "vimeoid",
        "vimeourl",
        "vimeoprovider"
    )
}

function Copy-AppCatalogProjection($Value) {
    if ($null -eq $Value -or $Value -is [string] -or $Value.GetType().IsPrimitive -or $Value -is [decimal]) {
        return $Value
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $copy = [ordered]@{}
        foreach ($key in $Value.Keys) {
            if ([string]$key -cne "pl" -and -not (Test-DormantVideoMetadataKey ([string]$key))) {
                $copy[[string]$key] = Copy-AppCatalogProjection $Value[$key]
            }
        }
        return $copy
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        $copy = [ordered]@{}
        foreach ($property in $Value.PSObject.Properties) {
            if ($property.Name -cne "pl" -and -not (Test-DormantVideoMetadataKey $property.Name)) {
                $copy[$property.Name] = Copy-AppCatalogProjection $property.Value
            }
        }
        return $copy
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $items = New-Object System.Collections.ArrayList
        foreach ($item in $Value) {
            [void]$items.Add((Copy-AppCatalogProjection $item))
        }
        return ,@($items)
    }
    return $Value
}

function ConvertTo-CanonicalNode($Value) {
    if ($null -eq $Value -or $Value -is [string] -or $Value.GetType().IsPrimitive -or $Value -is [decimal]) {
        return $Value
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | Sort-Object)) {
            $ordered[[string]$key] = ConvertTo-CanonicalNode $Value[$key]
        }
        return $ordered
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        $ordered = [ordered]@{}
        foreach ($property in @($Value.PSObject.Properties | Sort-Object Name)) {
            $ordered[$property.Name] = ConvertTo-CanonicalNode $property.Value
        }
        return $ordered
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $items = New-Object System.Collections.ArrayList
        foreach ($item in $Value) {
            [void]$items.Add((ConvertTo-CanonicalNode $item))
        }
        return ,@($items)
    }
    return $Value
}

function ConvertTo-CanonicalJson($Value) {
    $canonical = ConvertTo-CanonicalNode $Value
    return ConvertTo-Json -InputObject $canonical -Depth 100 -Compress
}

function Contains-PropertyName($Value, [string]$Name) {
    if ($null -eq $Value -or $Value -is [string] -or $Value.GetType().IsPrimitive -or $Value -is [decimal]) {
        return $false
    }
    if ($Value -is [System.Collections.IDictionary]) {
        foreach ($key in $Value.Keys) {
            if ([string]$key -ieq $Name) { return $true }
            if (Contains-PropertyName $Value[$key] $Name) { return $true }
        }
        return $false
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        foreach ($property in $Value.PSObject.Properties) {
            if ($property.Name -ieq $Name) { return $true }
            if (Contains-PropertyName $property.Value $Name) { return $true }
        }
        return $false
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) {
            if (Contains-PropertyName $item $Name) { return $true }
        }
    }
    return $false
}

function Contains-DormantVideoMetadata($Value) {
    if ($null -eq $Value -or $Value -is [string] -or $Value.GetType().IsPrimitive -or $Value -is [decimal]) {
        return $false
    }
    if ($Value -is [System.Collections.IDictionary]) {
        foreach ($key in $Value.Keys) {
            if (Test-DormantVideoMetadataKey ([string]$key)) { return $true }
            if (Contains-DormantVideoMetadata $Value[$key]) { return $true }
        }
        return $false
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        foreach ($property in $Value.PSObject.Properties) {
            if (Test-DormantVideoMetadataKey $property.Name) { return $true }
            if (Contains-DormantVideoMetadata $property.Value) { return $true }
        }
        return $false
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) {
            if (Contains-DormantVideoMetadata $item) { return $true }
        }
    }
    return $false
}

function Require-EnglishField($Container, [string]$FieldName, [string]$Location) {
    $localized = Get-PropertyValue $Container $FieldName
    if ($null -eq $localized -or -not (Has-Property $localized "en") -or -not (Has-Text (Get-PropertyValue $localized "en"))) {
        Add-Error "$Location requires nonblank $FieldName.en text"
    }
}

function Try-GetInteger($Value, [ref]$Result) {
    if ($null -eq $Value) { return $false }
    $parsed = [long]0
    $ok = [long]::TryParse(
        [string]$Value,
        [System.Globalization.NumberStyles]::Integer,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref]$parsed
    )
    if ($ok) { $Result.Value = $parsed }
    return $ok
}

function Get-TotalDoseUnits($Value, [long]$Sets) {
    if ($null -eq $Value -or $Sets -lt 1) { return [long]0 }
    $token = ([string]$Value).Trim()
    $perSideMultiplier = if ($token -match '/side(?:-focus)?$') { [long]2 } else { [long]1 }
    $token = [regex]::Replace($token, '/side(?:-focus)?$', '')
    $numbers = @([regex]::Matches($token, '\d+') | ForEach-Object { [long]$_.Value })
    if ($numbers.Count -lt 1) { return [long]0 }

    $unitTotal = [long]0
    if ($token.Contains('-')) {
        foreach ($number in $numbers) { $unitTotal += $number }
    } elseif ($token.Contains('+')) {
        $clusterTotal = [long]0
        foreach ($number in $numbers) { $clusterTotal += $number }
        $unitTotal = $clusterTotal * $Sets
    } else {
        $unitTotal = $numbers[0] * $Sets
    }
    return $unitTotal * $perSideMultiplier
}

function Get-NumericTempoSecondsPerRep($Tempo) {
    if ($null -eq $Tempo) { return [long]0 }
    $token = ([string]$Tempo).Trim()
    if ($token -match '^[0-9Xx]{4}$') {
        $seconds = [long]0
        foreach ($character in $token.ToCharArray()) {
            if ([char]::IsDigit($character)) {
                $seconds += [long]::Parse([string]$character)
            } elseif ($character -eq 'X' -or $character -eq 'x') {
                # An explosive phase still consumes real clock time; one second
                # is the conservative minimum for duration metadata.
                $seconds += 1
            }
        }
        return $seconds
    }
    if ($token -match '^(\d+)-pause-(\d+)$') {
        return [long]$Matches[1] + 1 + [long]$Matches[2]
    }
    if ($token -match '^(\d+)-second(?:_|-).+$') { return [long]$Matches[1] }
    $wordSeconds = @{
        one = 1; two = 2; three = 3; four = 4; five = 5
        six = 6; seven = 7; eight = 8; nine = 9; ten = 10
    }
    if ($token -match '^([a-z]+)_second_lower$' -and $wordSeconds.ContainsKey($Matches[1])) {
        return [long]$wordSeconds[$Matches[1]]
    }
    return [long]0
}

function Get-ExplicitEccentricSecondsPerRep($Tempo) {
    if ($null -eq $Tempo) { return [long]0 }
    $token = ([string]$Tempo).Trim().ToLowerInvariant()
    if ($token -match '^(\d)[0-9xX]{3}$') { return [long]$Matches[1] }
    if ($token -match '^(\d+)-pause-\d+$') { return [long]$Matches[1] }
    if ($token -match '^(\d+)-second(?:_|-).+$') { return [long]$Matches[1] }
    $wordSeconds = @{
        one = 1; two = 2; three = 3; four = 4; five = 5
        six = 6; seven = 7; eight = 8; nine = 9; ten = 10
    }
    if ($token -match '^([a-z]+)_second_lower$' -and $wordSeconds.ContainsKey($Matches[1])) {
        return [long]$wordSeconds[$Matches[1]]
    }
    return [long]0
}

function Get-ExactMinimumSessionSeconds($Program) {
    $minimumSeconds = [long]0
    foreach ($sectionName in @("warmup", "main", "cooldown")) {
        foreach ($step in @((Get-PropertyValue $Program $sectionName))) {
            $values = @($step)
            if ($values.Count -ne 7) { continue }
            $stepSets = [long]0
            $stepRestSeconds = [long]0
            if (-not (Try-GetInteger $values[1] ([ref]$stepSets)) -or $stepSets -lt 1) { continue }
            if ((Try-GetInteger $values[4] ([ref]$stepRestSeconds)) -and $stepRestSeconds -ge 0) {
                $minimumSeconds += [Math]::Max([long]0, $stepSets - 1) * $stepRestSeconds
            }
            if ($null -ne $values[3]) {
                $minimumSeconds += Get-TotalDoseUnits $values[3] $stepSets
            } elseif ($null -ne $values[2]) {
                $tempoSeconds = Get-NumericTempoSecondsPerRep $values[5]
                if ($tempoSeconds -gt 0) {
                    $minimumSeconds += (Get-TotalDoseUnits $values[2] $stepSets) * $tempoSeconds
                }
            }
        }
    }
    return $minimumSeconds
}

function Validate-AmountToken($Value, [long]$Sets, [string]$Location, [string]$Label, [long]$Maximum) {
    $token = [string]$Value
    if ([string]::IsNullOrWhiteSpace($token)) {
        Add-Error "$Location has a blank $Label value"
        return
    }
    $validPattern = '^\d+(?:(?:-\d+)+|(?:\+\d+)+)?(?:/side(?:-focus)?)?$'
    if ($token -notmatch $validPattern) {
        Add-Error "$Location has invalid $Label token '$token'"
        return
    }
    $numbers = @([regex]::Matches($token, '\d+') | ForEach-Object { [long]$_.Value })
    foreach ($number in $numbers) {
        if ($number -lt 1 -or $number -gt $Maximum) {
            Add-Error "$Location has out-of-range $Label value '$number'"
        }
    }
    if ($token.Contains("-") -and -not $token.EndsWith("-focus") -and $numbers.Count -ne $Sets) {
        Add-Error "$Location has $($numbers.Count) per-set $Label values for $Sets sets"
    }
}

function Validate-SecondsToken($Value, [long]$Sets, [string]$Location) {
    if ($Value -isnot [string]) {
        Validate-AmountToken $Value $Sets $Location "seconds" 3600
        return
    }

    $token = [string]$Value
    if ($token -notmatch '^[0-9]+(?:-[0-9]+)*(?:/side)?$') {
        Add-Error "$Location has invalid nonnumeric seconds token '$token'"
        return
    }
    $numbers = @([regex]::Matches($token, '[0-9]+') | ForEach-Object { [long]$_.Value })
    foreach ($number in $numbers) {
        if ($number -lt 1 -or $number -gt 600) {
            Add-Error "$Location has out-of-range seconds component '$number'"
        }
    }
    if ($token.Contains("-") -and $numbers.Count -ne $Sets) {
        Add-Error "$Location has $($numbers.Count) duration ladder values for $Sets sets"
    }
}

function Validate-Step($Step, [string]$Location, $MovementById) {
    $values = @($Step)
    if ($values.Count -ne 7) {
        Add-Error "$Location must have exactly 7 tuple values"
        return
    }

    $movementId = [string]$values[0]
    if (-not (Has-Text $movementId) -or -not $MovementById.ContainsKey($movementId)) {
        Add-Error "$Location references unknown movement '$movementId'"
    }

    $sets = [long]0
    if (-not (Try-GetInteger $values[1] ([ref]$sets)) -or $sets -lt 1 -or $sets -gt 20) {
        Add-Error "$Location has invalid set count '$($values[1])'"
        $sets = 1
    }

    $hasReps = $null -ne $values[2]
    $hasSeconds = $null -ne $values[3]
    if ($hasReps -eq $hasSeconds) {
        Add-Error "$Location must specify reps or seconds, but not both"
    }
    if ($movementId -in @("one_leg_front", "one_leg_back") -and (-not $hasSeconds -or $hasReps)) {
        Add-Error "$Location must encode the unilateral lever hold in the seconds slot"
    } elseif ($movementId -in @("one_leg_front", "one_leg_back") -and
        ($sets % 2) -ne 0 -and
        ($values[3] -isnot [string] -or [string]$values[3] -notmatch '/side$')) {
        Add-Error "$Location uses odd one-leg lever sets without an explicit per-side duration"
    }
    if ($movementId -in @("bent_dragon_negative", "full_dragon_negative", "dragon_flag", "candlestick") -and
        $hasReps -and [string]$values[2] -match '/side') {
        Add-Error "$Location prescribes a bilateral dragon movement per side"
    }
    $explicitPerSideRepMovements = @(
        "ankle_rocks",
        "bird_dog",
        "cossack_squat",
        "dead_bug",
        "leg_swings",
        "reverse_lunge",
        "single_leg_bridge",
        "split_squat",
        "thoracic_rotation"
    )
    if ($movementId -in $explicitPerSideRepMovements -and $hasReps -and
        ($values[2] -isnot [string] -or [string]$values[2] -notmatch '/side$')) {
        Add-Error "$Location must prescribe unilateral repetitions explicitly per side"
    }
    if ($movementId -eq "side_plank" -and $hasSeconds -and
        ($values[3] -isnot [string] -or [string]$values[3] -notmatch '/side$')) {
        Add-Error "$Location must prescribe side-plank duration explicitly per side"
    }
    if ($movementId -eq "one_leg_dragon_negative" -and
        $hasReps -and [string]$values[2] -notmatch '/side$') {
        Add-Error "$Location must prescribe one-leg dragon repetitions explicitly per side"
    }
    if ($hasReps) { Validate-AmountToken $values[2] $sets $Location "reps" 500 }
    if ($hasSeconds) { Validate-SecondsToken $values[3] $sets $Location }

    $rest = [long]0
    if (-not (Try-GetInteger $values[4] ([ref]$rest)) -or $rest -lt 0 -or $rest -gt 600) {
        Add-Error "$Location has invalid rest value '$($values[4])'"
    }
    if ($null -ne $values[5] -and -not (Has-Text $values[5])) {
        Add-Error "$Location has a blank tempo token"
    }

    $regressionId = [string]$values[6]
    if ((Has-Text $regressionId) -and -not $MovementById.ContainsKey($regressionId)) {
        Add-Error "$Location references unknown regression '$regressionId'"
    } elseif ((Has-Text $regressionId) -and $regressionId -eq $movementId) {
        Add-Error "$Location uses its movement as its own regression"
    } elseif (Has-Text $regressionId) {
        # Following regression_id always moves toward an easier movement. If an
        # explicit alternative eventually regresses back to the prescribed
        # movement, that alternative is a harder descendant, not a regression.
        $visitedRegressionIds = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
        $candidateId = $regressionId
        while ((Has-Text $candidateId) -and $MovementById.ContainsKey($candidateId) -and $visitedRegressionIds.Add($candidateId)) {
            if ($candidateId -eq $movementId) {
                Add-Error "$Location uses harder descendant '$regressionId' as the regression for '$movementId'"
                break
            }
            $candidateId = [string]$MovementById[$candidateId].regression_id
        }

        $isCanonicalRegression = $false
        $visitedCanonicalIds = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
        $canonicalId = [string]$MovementById[$movementId].regression_id
        while ((Has-Text $canonicalId) -and $MovementById.ContainsKey($canonicalId) -and $visitedCanonicalIds.Add($canonicalId)) {
            if ($canonicalId -eq $regressionId) {
                $isCanonicalRegression = $true
                break
            }
            $canonicalId = [string]$MovementById[$canonicalId].regression_id
        }
        $regressionPair = "$movementId->$regressionId"
        if (-not $isCanonicalRegression -and -not $script:allowedLateralRegressionPairs.Contains($regressionPair)) {
            Add-Error "$Location uses unreviewed lateral regression '$regressionPair'"
        }
    }
}

function Test-RecoveryLike([string]$DayType) {
    if (-not (Has-Text $DayType)) { return $false }
    if ($DayType -match '(?i)(recovery|restore|reset)') { return $true }
    if ($DayType -match '(?i)mobility' -and $DayType -notmatch '(?i)strength') { return $true }
    return $false
}

function Get-ProgramTokenText($Programs, $MovementById) {
    $tokens = New-Object System.Collections.ArrayList
    foreach ($program in @($Programs)) {
        foreach ($step in @((Get-PropertyValue $program "main"))) {
            $values = @($step)
            if ($values.Count -lt 1) { continue }
            $movementId = [string]$values[0]
            if (Has-Text $movementId) { [void]$tokens.Add($movementId.ToLowerInvariant()) }
            if ($MovementById.ContainsKey($movementId)) {
                foreach ($pattern in @($MovementById[$movementId].patterns)) {
                    if (Has-Text $pattern) { [void]$tokens.Add(([string]$pattern).ToLowerInvariant()) }
                }
            }
        }
    }
    return (@($tokens) -join " ")
}

if (-not (Test-Path -LiteralPath $CatalogPath -PathType Leaf)) { throw "Catalog not found: $CatalogPath" }
if (-not (Test-Path -LiteralPath $BaselinePath -PathType Leaf)) { throw "Baseline not found: $BaselinePath" }

$baselineHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaselinePath).Hash.ToUpperInvariant()
if ($baselineHash -cne $expectedBaselineSha256) {
    Add-Error "Baseline SHA-256 mismatch: expected $expectedBaselineSha256, found $baselineHash"
}

$catalogRaw = Get-Content -Raw -Encoding UTF8 -LiteralPath $CatalogPath
if ($catalogRaw.Contains([string][char]0xFFFD)) {
    Add-Error "Final generated catalog contains a Unicode replacement character"
}
$catalog = $catalogRaw | ConvertFrom-Json
$baseline = Get-Content -Raw -Encoding UTF8 -LiteralPath $BaselinePath | ConvertFrom-Json

if (Contains-PropertyName $catalog "pl") {
    Add-Error "Final generated catalog contains at least one forbidden 'pl' key"
}
if (Contains-PropertyName $catalog "routing") {
    Add-Error "Final generated catalog contains a forbidden routing field"
}
if (Contains-DormantVideoMetadata $catalog) {
    Add-Error "Final generated catalog contains dormant video id, URL, or provider metadata"
}
if ([string]$catalog.schema_version -cne "2.0.0") { Add-Error "schema_version must be 2.0.0" }
if ([string]$catalog.source_baseline.sha256 -cne $expectedBaselineSha256) { Add-Error "Catalog source_baseline.sha256 is missing or incorrect" }
if (-not [bool]$catalog.source_baseline.immutable) { Add-Error "source_baseline.immutable must be true" }
if ([int]$catalog.source_baseline.authentic_program_count -ne 90) { Add-Error "source_baseline authentic count must be 90" }
if ([int]$catalog.source_baseline.placeholder_count -ne 289) { Add-Error "source_baseline placeholder count must be 289" }
if ([int]$catalog.source_baseline.included_placeholder_count -ne 180) { Add-Error "source_baseline included placeholder count must be 180" }
if ([int]$catalog.source_baseline.archived_placeholder_count -ne 109) { Add-Error "source_baseline archived placeholder count must be 109" }
if ([int]$catalog.source_baseline.app_slot_limit_per_level -ne 100) { Add-Error "source_baseline app slot limit must be 100" }
Require-EnglishField $catalog.catalog_provenance "notice" "catalog_provenance"

$movementById = @{}
foreach ($movement in @($catalog.movement_library)) {
    $movementId = [string]$movement.id
    if (-not (Has-Text $movementId)) {
        Add-Error "Movement without an id"
        continue
    }
    if ($movementById.ContainsKey($movementId)) {
        Add-Error "Duplicate movement id: $movementId"
        continue
    }
    $movementById[$movementId] = $movement
    Require-EnglishField $movement "name" "Movement $movementId"
    Require-EnglishField $movement "description" "Movement $movementId"
    Require-EnglishField $movement "safety" "Movement $movementId"
    if (-not (Has-Text $movement.category)) { Add-Error "Movement $movementId has no category" }
    $patterns = @($movement.patterns)
    if ($patterns.Count -lt 1 -or @($patterns | Where-Object { -not (Has-Text $_) }).Count -gt 0) {
        Add-Error "Movement $movementId requires at least one nonblank pattern"
    }
}
if ($movementById.Count -lt 115) { Add-Error "Expected at least 115 unique movements, found $($movementById.Count)" }
foreach ($movementId in $movementById.Keys) {
    $regressionId = [string]$movementById[$movementId].regression_id
    if (Has-Text $regressionId) {
        if (-not $movementById.ContainsKey($regressionId)) { Add-Error "Movement $movementId references unknown regression '$regressionId'" }
        elseif ($regressionId -eq $movementId) { Add-Error "Movement $movementId regresses to itself" }
    }
}
foreach ($movementId in $movementById.Keys) {
    $regressionPath = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
    $currentMovementId = $movementId
    while (Has-Text $currentMovementId) {
        if (-not $regressionPath.Add($currentMovementId)) {
            Add-Error "Movement $movementId has a cyclic regression chain at '$currentMovementId'"
            break
        }
        if (-not $movementById.ContainsKey($currentMovementId)) { break }
        $currentMovementId = [string]$movementById[$currentMovementId].regression_id
    }
}

# Validate all 619 authored source records, including the 109 owner-research
# drafts that are intentionally archived outside the 600-slot app catalog.
$authoredSourceProgramIds = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
$authoredSourceTitles = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
$authoredSourceDescriptions = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
$authoredSourceMainSignatures = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
$authoredSourceProgramCount = 0
foreach ($authoredSourcePath in $AuthoredSourcePaths) {
    if (-not (Test-Path -LiteralPath $authoredSourcePath -PathType Leaf)) {
        Add-Error "Authored source file was not found: $authoredSourcePath"
        continue
    }
    $authoredSourceRaw = Get-Content -Raw -Encoding UTF8 -LiteralPath $authoredSourcePath
    if ($authoredSourceRaw.Contains([string][char]0xFFFD)) {
        Add-Error "Authored source contains a Unicode replacement character: $authoredSourcePath"
    }
    $authoredSource = $authoredSourceRaw | ConvertFrom-Json
    if (Contains-PropertyName $authoredSource "pl") {
        Add-Error "Authored source contains at least one forbidden 'pl' key: $authoredSourcePath"
    }
    foreach ($sourceProgram in @($authoredSource.programs)) {
        $authoredSourceProgramCount++
        $sourceProgramId = [string]$sourceProgram.id
        $sourceLocation = "$authoredSourcePath program $sourceProgramId"
        if (-not (Has-Text $sourceProgramId)) {
            Add-Error "$authoredSourcePath contains a program without an id"
        } elseif (-not $authoredSourceProgramIds.Add($sourceProgramId)) {
            Add-Error "Duplicate authored source program id: $sourceProgramId"
        }
        Require-EnglishField $sourceProgram "title" $sourceLocation
        Require-EnglishField $sourceProgram "description" $sourceLocation
        $sourceTitle = [string](Get-PropertyValue (Get-PropertyValue $sourceProgram "title") "en")
        $sourceDescription = [string](Get-PropertyValue (Get-PropertyValue $sourceProgram "description") "en")
        if ((Has-Text $sourceTitle) -and -not $authoredSourceTitles.Add($sourceTitle.Trim())) {
            Add-Error "$sourceLocation duplicates an authored source title: '$sourceTitle'"
        }
        if ((Has-Text $sourceDescription) -and -not $authoredSourceDescriptions.Add($sourceDescription.Trim())) {
            Add-Error "$sourceLocation duplicates an authored source description"
        }
        $sourceMainSignature = ConvertTo-CanonicalJson (Get-PropertyValue $sourceProgram "main")
        if (-not $authoredSourceMainSignatures.Add($sourceMainSignature)) {
            Add-Error "$sourceLocation duplicates an authored source main prescription"
        }
        $sourceSemanticText = ConvertTo-CanonicalJson $sourceProgram
        $sourceForbiddenChoicePattern = '(?i)(choose|choice|optional|independent|\broutes?\b|routing|non[-_ ]?blocking|never[-_ ]?gates?|never\s+blocks?|does\s+not\s+(?:block|prevent)|cannot\s+block|no\s+other\s+tracks?\s+depends?|restart\s+only\s+the\s+tracks?|separate\s+skills?\s+results?)'
        if ($sourceSemanticText -match $sourceForbiddenChoicePattern) {
            Add-Error "$sourceLocation contains user-choice or independent-route semantics: '$($Matches[0])'"
        }
        if (Contains-PropertyName $sourceProgram "routing") {
            Add-Error "$sourceLocation contains a forbidden routing field"
        }
        foreach ($sectionName in @("warmup", "main", "cooldown")) {
            $sourceSteps = @((Get-PropertyValue $sourceProgram $sectionName))
            if ($sourceSteps.Count -lt 1) {
                Add-Error "$sourceLocation has no $sectionName steps"
                continue
            }
            for ($sourceStepIndex = 0; $sourceStepIndex -lt $sourceSteps.Count; $sourceStepIndex++) {
                Validate-Step $sourceSteps[$sourceStepIndex] "$sourceLocation $sectionName $($sourceStepIndex + 1)" $movementById
            }
        }
        if ([int]$sourceProgram.number -gt 100) {
            $sourceMinutes = [long]0
            if (-not (Try-GetInteger (Get-PropertyValue (Get-PropertyValue $sourceProgram "schedule") "minutes") ([ref]$sourceMinutes)) -or
                $sourceMinutes -lt 15 -or $sourceMinutes -gt 75) {
                Add-Error "$sourceLocation has invalid archived schedule minutes"
            } else {
                $sourceExactMinimumSeconds = Get-ExactMinimumSessionSeconds $sourceProgram
                if ($sourceExactMinimumSeconds -gt ($sourceMinutes * 60)) {
                    $sourceExactMinimumMinutes = [Math]::Round($sourceExactMinimumSeconds / 60.0, 1)
                    Add-Error "$sourceLocation exact timed work plus inter-set rest needs at least $sourceExactMinimumMinutes minutes, exceeding its $sourceMinutes-minute schedule"
                }
            }
        }
    }
}
if ($authoredSourceProgramCount -ne 619) {
    Add-Error "Expected 619 authored source programs including archive, found $authoredSourceProgramCount"
}
if ($authoredSourceProgramIds.Count -ne 619) {
    Add-Error "Expected 619 unique authored source program ids, found $($authoredSourceProgramIds.Count)"
}

$profileById = @{}
foreach ($profile in @($catalog.coaching_profiles)) {
    $profileId = [string]$profile.id
    if (-not (Has-Text $profileId)) { Add-Error "Coaching profile without an id"; continue }
    if ($profileById.ContainsKey($profileId)) { Add-Error "Duplicate coaching profile id: $profileId"; continue }
    $profileById[$profileId] = $profile
    Require-EnglishField $profile "title" "Coaching profile $profileId"
    $profileWarmup = @($profile.warmup)
    $profileCooldown = @($profile.cooldown)
    if ($profileWarmup.Count -lt 3) { Add-Error "Coaching profile $profileId has fewer than 3 warm-up steps" }
    if ($profileCooldown.Count -lt 3) { Add-Error "Coaching profile $profileId has fewer than 3 cooldown steps" }
    for ($index = 0; $index -lt $profileWarmup.Count; $index++) {
        Validate-Step $profileWarmup[$index] "Coaching profile $profileId warmup $($index + 1)" $movementById
    }
    for ($index = 0; $index -lt $profileCooldown.Count; $index++) {
        Validate-Step $profileCooldown[$index] "Coaching profile $profileId cooldown $($index + 1)" $movementById
    }
    $profileStretchCount = @($profileCooldown | Where-Object {
        $stepValues = @($_)
        $stepValues.Count -eq 7 -and
            $movementById.ContainsKey([string]$stepValues[0]) -and
            [string]$movementById[[string]$stepValues[0]].category -in @("static_stretch", "static_cooldown")
    }).Count
    if ($profileStretchCount -lt 2) {
        Add-Error "Coaching profile $profileId has fewer than 2 explicit cooldown stretches"
    }
}
if ($profileById.Count -lt 1) { Add-Error "No coaching profiles were found" }

$baselineBySlot = @{}
$baselineDetailedCount = 0
$baselinePlaceholderCount = 0
foreach ($baselineLevel in @($baseline.levels)) {
    foreach ($baselineProgram in @($baselineLevel.programs)) {
        $slot = "$([int]$baselineLevel.number)-$([int]$baselineProgram.number)"
        $baselineBySlot[$slot] = $baselineProgram
        if ([bool]$baselineProgram.has_details) { $baselineDetailedCount++ } else { $baselinePlaceholderCount++ }
    }
}
if ($baselineDetailedCount -ne 90 -or $baselinePlaceholderCount -ne 289) {
    Add-Error "Baseline content counts changed: detailed=$baselineDetailedCount placeholders=$baselinePlaceholderCount"
}

$levels = @($catalog.levels)
$expectedCounts = @{ 1 = 100; 2 = 100; 3 = 100; 4 = 100; 5 = 100; 6 = 100 }
if ($levels.Count -ne 6) { Add-Error "Expected 6 levels, found $($levels.Count)" }
$seenLevels = New-Object 'System.Collections.Generic.HashSet[int]'
$programIds = New-Object 'System.Collections.Generic.HashSet[string]'
$globalAuthoredTitles = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
$globalAuthoredDescriptions = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
$globalAuthoredMainSignatures = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
$originCounts = @{ apk_authentic = 0; reconstructed_placeholder = 0; llm_extension = 0; new_level = 0 }
$totalPrograms = 0

foreach ($level in $levels) {
    $levelNumber = [int]$level.number
    if (-not $seenLevels.Add($levelNumber)) { Add-Error "Duplicate level number: $levelNumber" }
    if (-not $expectedCounts.ContainsKey($levelNumber)) {
        Add-Error "Unexpected level number: $levelNumber"
        continue
    }

    $expectedLevelId = "sg:l{0:D2}" -f $levelNumber
    if ([string]$level.id -cne $expectedLevelId) {
        Add-Error "Level $levelNumber id must be $expectedLevelId"
    }
    Require-EnglishField $level "name" "Level $levelNumber"
    Require-EnglishField $level "description" "Level $levelNumber"

    $programs = @($level.programs)
    $expectedProgramCount = [int]$expectedCounts[$levelNumber]
    if ($programs.Count -ne $expectedProgramCount) {
        Add-Error "Level $levelNumber expected $expectedProgramCount programs, found $($programs.Count)"
    }
    $totalPrograms += $programs.Count

    $numbers = @($programs | ForEach-Object { [int]$_.number } | Sort-Object)
    for ($numberIndex = 0; $numberIndex -lt $expectedProgramCount; $numberIndex++) {
        $expectedNumber = $numberIndex + 1
        if ($numberIndex -ge $numbers.Count -or $numbers[$numberIndex] -ne $expectedNumber) {
            Add-Error "Level $levelNumber program numbers are not contiguous at $expectedNumber"
            break
        }
    }

    $titleSet = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
    $mainSignatureSet = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)

    foreach ($program in $programs) {
        $programNumber = [int]$program.number
        $location = "Level $levelNumber program $programNumber"
        $expectedProgramId = "sg:l{0:D2}:p{1:D4}" -f $levelNumber, $programNumber

        if ([string]$program.id -cne $expectedProgramId) { Add-Error "$location id must be $expectedProgramId" }
        if (-not $programIds.Add([string]$program.id)) { Add-Error "Duplicate program id: $($program.id)" }
        if ([int]$program.level -ne $levelNumber) { Add-Error "$location embeds the wrong level" }

        Require-EnglishField $program "title" $location
        Require-EnglishField $program "description" $location
        $titleText = [string](Get-PropertyValue (Get-PropertyValue $program "title") "en")
        $normalizedTitle = $titleText.Trim()
        $descriptionText = [string](Get-PropertyValue (Get-PropertyValue $program "description") "en")
        $normalizedDescription = $descriptionText.Trim()
        if ((Has-Text $normalizedTitle) -and -not $titleSet.Add($normalizedTitle)) {
            Add-Error "$location duplicates the level title '$normalizedTitle'"
        }

        $origin = [string]$program.origin
        if (-not $originCounts.ContainsKey($origin)) {
            Add-Error "$location has unknown origin '$origin'"
        } else {
            $originCounts[$origin] = [int]$originCounts[$origin] + 1
        }
        if ($origin -ne "apk_authentic") {
            if ((Has-Text $normalizedTitle) -and -not $globalAuthoredTitles.Add($normalizedTitle)) {
                Add-Error "$location duplicates a title used by another authored workout"
            }
            if ((Has-Text $normalizedDescription) -and -not $globalAuthoredDescriptions.Add($normalizedDescription)) {
                Add-Error "$location duplicates a description used by another authored workout"
            }
        }

        $slot = "$levelNumber-$programNumber"
        $baselineProgram = $null
        $expectedOrigin = "new_level"
        if ($levelNumber -le 3) {
            if ($baselineBySlot.ContainsKey($slot)) {
                $baselineProgram = $baselineBySlot[$slot]
                if ([bool]$baselineProgram.has_details) { $expectedOrigin = "apk_authentic" }
                else { $expectedOrigin = "reconstructed_placeholder" }
            } elseif ($levelNumber -eq 3 -and $programNumber -ge 71 -and $programNumber -le 100) {
                $expectedOrigin = "llm_extension"
            } else {
                Add-Error "$location has no matching baseline slot"
            }
        }
        if ($origin -cne $expectedOrigin) { Add-Error "$location origin must be $expectedOrigin, found $origin" }

        $schedule = Get-PropertyValue $program "schedule"
        if ($null -eq $schedule) {
            Add-Error "$location has no schedule"
            continue
        }
        $week = [long]0
        $day = [long]0
        if (-not (Try-GetInteger (Get-PropertyValue $schedule "week") ([ref]$week)) -or $week -lt 1 -or $week -gt 100) {
            Add-Error "$location has invalid schedule.week"
        }
        if (-not (Try-GetInteger (Get-PropertyValue $schedule "day") ([ref]$day)) -or $day -lt 1 -or $day -gt 7) {
            Add-Error "$location has invalid schedule.day"
        }
        if ($origin -eq "apk_authentic") {
            $expectedWeek = [long][Math]::Floor(($programNumber - 1) / 7) + 1
            $expectedDay = [long](($programNumber - 1) % 7) + 1
            if ($week -ne $expectedWeek -or $day -ne $expectedDay) {
                Add-Error "$location authentic schedule must be week $expectedWeek day $expectedDay"
            }
        }
        $dayType = [string](Get-PropertyValue $schedule "day_type")
        if (-not (Has-Text $dayType)) { Add-Error "$location has blank schedule.day_type" }
        $focusItems = @((Get-PropertyValue $schedule "focus"))
        if ($focusItems.Count -lt 1 -or @($focusItems | Where-Object { -not (Has-Text $_) }).Count -gt 0) {
            Add-Error "$location requires at least one nonblank schedule.focus value"
        }

        if ($origin -eq "apk_authentic") {
            if ($dayType -cne "authentic_base") { Add-Error "$location authentic day_type must be authentic_base" }
            if (-not (Has-Property $schedule "minutes") -or $null -ne $schedule.minutes) {
                Add-Error "$location authentic schedule.minutes must be present and null"
            }
            if (-not (Has-Property $schedule "rpe") -or $null -ne $schedule.rpe) {
                Add-Error "$location authentic schedule.rpe must be present and null"
            }
            if ($null -eq $baselineProgram) {
                Add-Error "$location cannot verify authentic content without a baseline slot"
            } else {
                $expectedContent = Copy-AppCatalogProjection $baselineProgram
                if ((ConvertTo-CanonicalJson $program.content) -cne (ConvertTo-CanonicalJson $expectedContent)) {
                    Add-Error "$location authentic content differs from the English-only, video-free baseline projection"
                }
                if ([string]$program.legacy.workout_id -cne [string]$baselineProgram.id) {
                    Add-Error "$location legacy.workout_id does not match the baseline"
                }
                if ([bool]$program.legacy.pro -ne [bool]$baselineProgram.pro) {
                    Add-Error "$location legacy.pro does not match the baseline"
                }
                if ([bool]$program.legacy.has_details -ne [bool]$baselineProgram.has_details) {
                    Add-Error "$location legacy.has_details does not match the baseline"
                }
            }

            $overlay = Get-PropertyValue $program "coaching_overlay"
            if ($null -eq $overlay) {
                Add-Error "$location has no coaching_overlay"
            } else {
                if ([string]$overlay.origin -cne "new_coaching_overlay") {
                    Add-Error "$location coaching_overlay origin must be new_coaching_overlay"
                }
                $profileId = [string]$overlay.profile_id
                if (-not $profileById.ContainsKey($profileId)) {
                    Add-Error "$location references unknown coaching profile '$profileId'"
                } else {
                    $overlayWarmup = @($overlay.warmup)
                    $overlayCooldown = @($overlay.cooldown)
                    if ($overlayWarmup.Count -lt 3) { Add-Error "$location coaching warmup has fewer than 3 steps" }
                    if ($overlayCooldown.Count -lt 3) { Add-Error "$location coaching cooldown has fewer than 3 steps" }
                    for ($stepIndex = 0; $stepIndex -lt $overlayWarmup.Count; $stepIndex++) {
                        Validate-Step $overlayWarmup[$stepIndex] "$location coaching warmup $($stepIndex + 1)" $movementById
                    }
                    for ($stepIndex = 0; $stepIndex -lt $overlayCooldown.Count; $stepIndex++) {
                        Validate-Step $overlayCooldown[$stepIndex] "$location coaching cooldown $($stepIndex + 1)" $movementById
                    }
                    $profile = $profileById[$profileId]
                    if ((ConvertTo-CanonicalJson $overlay.warmup) -cne (ConvertTo-CanonicalJson $profile.warmup)) {
                        Add-Error "$location coaching warmup differs from profile $profileId"
                    }
                    if ((ConvertTo-CanonicalJson $overlay.cooldown) -cne (ConvertTo-CanonicalJson $profile.cooldown)) {
                        Add-Error "$location coaching cooldown differs from profile $profileId"
                    }
                }
            }
            continue
        }

        $minutes = [long]0
        $rpe = [long]0
        if (-not (Try-GetInteger (Get-PropertyValue $schedule "minutes") ([ref]$minutes)) -or $minutes -lt 15 -or $minutes -gt 75) {
            Add-Error "$location has invalid schedule.minutes"
        }
        if (-not (Try-GetInteger (Get-PropertyValue $schedule "rpe") ([ref]$rpe)) -or $rpe -lt 1 -or $rpe -gt 10) {
            Add-Error "$location has invalid schedule.rpe"
        } elseif ($rpe -gt 8) {
            Add-Error "$location exceeds the authored-session RPE 8 cap"
        } elseif ((Test-RecoveryLike $dayType) -and $rpe -gt 4) {
            Add-Error "$location is recovery-like but has RPE $rpe"
        }
        if (-not (Has-Text $program.authored_id)) { Add-Error "$location has blank authored_id" }

        if ($origin -eq "reconstructed_placeholder" -and $null -ne $baselineProgram) {
            if ([string]$program.legacy.workout_id -cne [string]$baselineProgram.id) {
                Add-Error "$location legacy.workout_id does not match its placeholder"
            }
            if ([bool]$program.legacy.pro -ne [bool]$baselineProgram.pro -or [bool]$program.legacy.has_details -ne [bool]$baselineProgram.has_details) {
                Add-Error "$location legacy flags do not match its placeholder"
            }
        }
        if ($origin -eq "new_level" -and $levelNumber -lt 4) {
            Add-Error "$location uses new_level below Level 4"
        }
        if ($origin -eq "llm_extension" -and ($levelNumber -ne 3 -or $programNumber -lt 71)) {
            Add-Error "$location uses llm_extension outside the Level 3 post-baseline range"
        }

        $warmup = @($program.warmup)
        $main = @($program.main)
        $cooldown = @($program.cooldown)
        if ($warmup.Count -lt 3) { Add-Error "$location has fewer than 3 warm-up steps" }
        if ($main.Count -lt 4) { Add-Error "$location has fewer than 4 main steps" }
        if ($cooldown.Count -lt 3) { Add-Error "$location has fewer than 3 cooldown steps" }
        $cooldownStretchCount = @($cooldown | Where-Object {
            $stepValues = @($_)
            $stepValues.Count -eq 7 -and
                $movementById.ContainsKey([string]$stepValues[0]) -and
                [string]$movementById[[string]$stepValues[0]].category -in @("static_stretch", "static_cooldown")
        }).Count
        if ($cooldownStretchCount -lt 2) {
            Add-Error "$location has fewer than 2 explicit cooldown stretches"
        }
        foreach ($sectionName in @("warmup", "main", "cooldown")) {
            $steps = @((Get-PropertyValue $program $sectionName))
            for ($stepIndex = 0; $stepIndex -lt $steps.Count; $stepIndex++) {
                Validate-Step $steps[$stepIndex] "$location $sectionName $($stepIndex + 1)" $movementById
            }
        }

        $semanticParts = New-Object System.Collections.ArrayList
        foreach ($semanticValue in @($normalizedTitle, $normalizedDescription, $dayType)) {
            if (Has-Text $semanticValue) { [void]$semanticParts.Add([string]$semanticValue) }
        }
        foreach ($focusValue in $focusItems) {
            if (Has-Text $focusValue) { [void]$semanticParts.Add([string]$focusValue) }
        }
        foreach ($semanticField in @("readiness", "safety")) {
            $semanticObject = Get-PropertyValue $program $semanticField
            if ($null -ne $semanticObject) {
                [void]$semanticParts.Add((ConvertTo-CanonicalJson $semanticObject))
            }
        }
        foreach ($sectionName in @("warmup", "main", "cooldown")) {
            foreach ($step in @((Get-PropertyValue $program $sectionName))) {
                $values = @($step)
                if ($values.Count -eq 7 -and (Has-Text $values[5])) {
                    [void]$semanticParts.Add([string]$values[5])
                }
            }
        }
        $semanticText = $semanticParts -join " "
        $forbiddenChoicePattern = '(?i)(choose|choice|optional|independent|\broutes?\b|routing|non[-_ ]?blocking|never[-_ ]?gates?|never\s+blocks?|does\s+not\s+(?:block|prevent)|cannot\s+block|no\s+other\s+tracks?\s+depends?|restart\s+only\s+the\s+tracks?|separate\s+skills?\s+results?)'
        if ($semanticText -match $forbiddenChoicePattern) {
            Add-Error "$location contains user-choice or independent-route semantics: '$($Matches[0])'"
        }
        $gate = Get-PropertyValue $program "gate"
        if ($null -ne $gate) {
            if ((Has-Property $gate "track") -or (Has-Property $gate "tracks")) {
                Add-Error "$location gate metadata splits the guided sequence into selectable tracks"
            }
            if ((Has-Property $gate "blocking") -and -not [bool](Get-PropertyValue $gate "blocking")) {
                Add-Error "$location gate metadata marks an assigned check as nonblocking"
            }
            $gateText = ConvertTo-CanonicalJson $gate
            if ($gateText -match $forbiddenChoicePattern -or
                $gateText -match '(?i)(three_of_five|planche_required|on_fail)') {
                Add-Error "$location gate metadata contradicts the collective guided sequence"
            }
        }

        $totalMainSets = [long]0
        $advancedMainSets = [long]0
        $dragonEccentricSessionSeconds = [long]0
        $advancedCategories = @("advanced_skill", "mastery_skill", "advanced_strength", "skill_strength")
        foreach ($step in $main) {
            $values = @($step)
            if ($values.Count -ne 7) { continue }
            $stepSets = [long]0
            if (-not (Try-GetInteger $values[1] ([ref]$stepSets))) { continue }
            $totalMainSets += $stepSets
            $movementId = [string]$values[0]
            if ($movementById.ContainsKey($movementId) -and
                $advancedCategories -contains [string]$movementById[$movementId].category) {
                $advancedMainSets += $stepSets
            }
            if ($movementId -in @("bent_dragon_negative", "one_leg_dragon_negative", "full_dragon_negative") -and
                $null -ne $values[2]) {
                $totalRepetitions = Get-TotalDoseUnits $values[2] $stepSets
                $eccentricSecondsPerRep = Get-ExplicitEccentricSecondsPerRep $values[5]
                $rowEccentricSeconds = $totalRepetitions * $eccentricSecondsPerRep
                $dragonEccentricSessionSeconds += $rowEccentricSeconds
                if ($rowEccentricSeconds -ge 90) {
                    Add-Error "$location prescribes $rowEccentricSeconds seconds of explicit dragon-negative lowering in one row; the cap is below 90 seconds"
                }
                if ($movementId -eq "full_dragon_negative") {
                    $tempoSecondsPerRep = Get-NumericTempoSecondsPerRep $values[5]
                    $rowTempoSeconds = $totalRepetitions * $tempoSecondsPerRep
                    if ($rowTempoSeconds -ge 120) {
                        Add-Error "$location prescribes $rowTempoSeconds seconds of explicit full-dragon-negative tempo work in one row; the cap is below 120 seconds"
                    }
                }
            }
        }
        if ($rpe -ge 8 -and $totalMainSets -gt 20) {
            Add-Error "$location has $totalMainSets main sets at RPE $rpe; the cap is 20"
        }
        if ($rpe -ge 8 -and $advancedMainSets -gt 10) {
            Add-Error "$location has $advancedMainSets advanced-category sets at RPE $rpe; the cap is 10"
        }
        if ($dragonEccentricSessionSeconds -ge 120) {
            Add-Error "$location accumulates $dragonEccentricSessionSeconds seconds of explicit dragon-negative lowering; the session cap is below 120 seconds"
        }

        $automaticRestSeconds = [long]0
        $exactWorkSeconds = [long]0
        foreach ($sectionName in @("warmup", "main", "cooldown")) {
            foreach ($step in @((Get-PropertyValue $program $sectionName))) {
                $values = @($step)
                if ($values.Count -ne 7) { continue }
                $stepSets = [long]0
                $stepRestSeconds = [long]0
                if ((Try-GetInteger $values[1] ([ref]$stepSets)) -and
                    (Try-GetInteger $values[4] ([ref]$stepRestSeconds)) -and
                    $stepSets -ge 1 -and $stepRestSeconds -ge 0) {
                    $automaticRestSeconds += ([Math]::Max([long]0, $stepSets - 1) * $stepRestSeconds)
                }
                if ($stepSets -ge 1 -and $null -ne $values[3]) {
                    $exactWorkSeconds += Get-TotalDoseUnits $values[3] $stepSets
                } elseif ($stepSets -ge 1 -and $null -ne $values[2]) {
                    $tempoSeconds = Get-NumericTempoSecondsPerRep $values[5]
                    if ($tempoSeconds -gt 0) {
                        $exactWorkSeconds += (Get-TotalDoseUnits $values[2] $stepSets) * $tempoSeconds
                    }
                }
            }
        }
        if ($minutes -ge 15 -and $minutes -le 75) {
            $scheduledSeconds = $minutes * 60
            $exactMinimumSeconds = $automaticRestSeconds + $exactWorkSeconds
            if ($exactMinimumSeconds -gt $scheduledSeconds) {
                $exactMinimumMinutes = [Math]::Round($exactMinimumSeconds / 60.0, 1)
                Add-Error "$location exact timed work plus inter-set rest needs at least $exactMinimumMinutes minutes, exceeding its $minutes-minute schedule"
            }
            if ($automaticRestSeconds -gt $scheduledSeconds) {
                Add-Error "$location automatic inter-set rest is $automaticRestSeconds seconds, exceeding its $minutes-minute schedule"
            } elseif (($automaticRestSeconds * 100) -gt ($scheduledSeconds * 85)) {
                $restPercentage = [Math]::Round(($automaticRestSeconds * 100.0) / $scheduledSeconds, 1)
                Add-Error "$location automatic inter-set rest uses $restPercentage percent of its $minutes-minute schedule; the cap is 85 percent"
            }
        }

        $mainSignature = ConvertTo-CanonicalJson $program.main
        if (-not $mainSignatureSet.Add($mainSignature)) {
            Add-Error "$location duplicates another authored main prescription in Level $levelNumber"
        }
        if (-not $globalAuthoredMainSignatures.Add($mainSignature)) {
            Add-Error "$location duplicates another authored main prescription in the catalog"
        }
    }

    $authoredPrograms = @($programs | Where-Object { [string]$_.origin -ne "apk_authentic" } | Sort-Object number)
    $consecutiveHardPrograms = 0
    $hardRunStartNumber = 0
    for ($authoredIndex = 0; $authoredIndex -lt $authoredPrograms.Count; $authoredIndex++) {
        $authoredProgram = $authoredPrograms[$authoredIndex]
        $expectedWeek = [long][Math]::Floor($authoredIndex / 7) + 1
        $expectedDay = [long]($authoredIndex % 7) + 1
        if ([long]$authoredProgram.schedule.week -ne $expectedWeek -or [long]$authoredProgram.schedule.day -ne $expectedDay) {
            Add-Error "Level $levelNumber authored program $($authoredProgram.number) schedule must be week $expectedWeek day $expectedDay"
        }
        if ([int]$authoredProgram.schedule.rpe -ge 7) {
            if ($consecutiveHardPrograms -eq 0) { $hardRunStartNumber = [int]$authoredProgram.number }
            $consecutiveHardPrograms++
            if ($consecutiveHardPrograms -eq 4) {
                Add-Error "Level $levelNumber has more than 3 consecutive authored RPE 7+ programs, starting at $hardRunStartNumber"
            }
        } else {
            $consecutiveHardPrograms = 0
            $hardRunStartNumber = 0
        }
    }
    foreach ($weekGroup in @($authoredPrograms | Group-Object { [int]$_.schedule.week })) {
        $weekPrograms = @($weekGroup.Group)
        if ($weekPrograms.Count -gt 7) {
            Add-Error "Level $levelNumber authored week $($weekGroup.Name) has more than 7 programs"
            continue
        }
        if ($weekPrograms.Count -eq 7) {
            $weekDays = @($weekPrograms | ForEach-Object { [int]$_.schedule.day } | Sort-Object -Unique)
            if ($weekDays.Count -ne 7 -or ($weekDays -join ',') -cne '1,2,3,4,5,6,7') {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) does not contain days 1 through 7 exactly once"
            }
            $tokenText = Get-ProgramTokenText $weekPrograms $movementById
            if ($tokenText -notmatch '(?:^|\s)(?:assisted_pullup|eccentric_pullup|pullup|chinup|inverted_row|high_inverted_row|feet_elevated_row)(?:\s|$)') {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) has no basic pull-up or row"
            }
            if ($tokenText -notmatch '(?:^|\s)(?:wall_pushup|incline_pushup|pushup|pause_pushup|close_pushup|pike_pushup)(?:\s|$)') {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) has no basic push-up"
            }
            if ($tokenText -notmatch '(?:^|\s)(?:assisted_dip|eccentric_dip|parallel_dip|straight_bar_dip|support_hold|foot_assisted_support|straight_bar_support)(?:\s|$)') {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) has no dip or support basic"
            }
            if ($tokenText -notmatch '(legs|squat|lunge|single_leg|calf)') {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) does not cover legs"
            }
            if ($tokenText -notmatch '(core|bodyline|hollow|plank|dragon|l_sit|compression)') {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) does not cover core"
            }
            if ($levelNumber -ge 3) {
                $weeklyGoalPatterns = [ordered]@{
                    muscle_up = 'muscle.?up'
                    front_lever = 'front_lever'
                    back_lever = 'back_lever'
                    dragon_flag = 'dragon'
                    planche = 'planche'
                }
                foreach ($goalName in $weeklyGoalPatterns.Keys) {
                    if ($tokenText -notmatch $weeklyGoalPatterns[$goalName]) {
                        Add-Error "Level $levelNumber authored week $($weekGroup.Name) does not train the $goalName goal in the parallel sequence"
                    }
                }
            }
            $lowRecoveryDays = @($weekPrograms | Where-Object {
                (Test-RecoveryLike ([string]$_.schedule.day_type)) -and [int]$_.schedule.rpe -le 4
            })
            if ($lowRecoveryDays.Count -lt 1) {
                Add-Error "Level $levelNumber authored week $($weekGroup.Name) has no low-RPE recovery-like day"
            }
        }
    }

    # A fixed seven-day calendar can begin on any workout, not only at a stored
    # week boundary. Enforce the same permanent basics, Level 3+ goal exposure,
    # and recovery requirement across every consecutive seven authored sessions.
    if ($authoredPrograms.Count -ge 7) {
        for ($windowStart = 0; $windowStart -le ($authoredPrograms.Count - 7); $windowStart++) {
            $windowPrograms = @($authoredPrograms | Select-Object -Skip $windowStart -First 7)
            $firstNumber = [int]$windowPrograms[0].number
            $lastNumber = [int]$windowPrograms[6].number
            $windowLabel = "Level $levelNumber authored rolling window $firstNumber-$lastNumber"
            $tokenText = Get-ProgramTokenText $windowPrograms $movementById

            if ($tokenText -notmatch '(?:^|\s)(?:assisted_pullup|eccentric_pullup|pullup|chinup|inverted_row|high_inverted_row|feet_elevated_row)(?:\s|$)') {
                Add-Error "$windowLabel has no basic pull-up or row"
            }
            if ($tokenText -notmatch '(?:^|\s)(?:wall_pushup|incline_pushup|pushup|pause_pushup|close_pushup|pike_pushup)(?:\s|$)') {
                Add-Error "$windowLabel has no basic push-up"
            }
            if ($tokenText -notmatch '(?:^|\s)(?:assisted_dip|eccentric_dip|parallel_dip|straight_bar_dip|support_hold|foot_assisted_support|straight_bar_support)(?:\s|$)') {
                Add-Error "$windowLabel has no dip or support basic"
            }
            if ($tokenText -notmatch '(legs|squat|lunge|single_leg|calf)') {
                Add-Error "$windowLabel does not cover legs"
            }
            if ($tokenText -notmatch '(core|bodyline|hollow|plank|dragon|l_sit|compression)') {
                Add-Error "$windowLabel does not cover core"
            }
            if ($levelNumber -ge 3) {
                $rollingGoalPatterns = [ordered]@{
                    muscle_up = 'muscle.?up'
                    front_lever = 'front_lever'
                    back_lever = 'back_lever'
                    dragon_flag = 'dragon'
                    planche = 'planche'
                }
                foreach ($goalName in $rollingGoalPatterns.Keys) {
                    if ($tokenText -notmatch $rollingGoalPatterns[$goalName]) {
                        Add-Error "$windowLabel does not train the $goalName goal in the parallel sequence"
                    }
                }
            }
            $lowRecoveryDays = @($windowPrograms | Where-Object {
                (Test-RecoveryLike ([string]$_.schedule.day_type)) -and [int]$_.schedule.rpe -le 4
            })
            if ($lowRecoveryDays.Count -lt 1) {
                Add-Error "$windowLabel has no low-RPE recovery-like day"
            }
        }
    }

    if ($levelNumber -ge 4) {
        $levelTokenText = Get-ProgramTokenText $authoredPrograms $movementById
        $goalPatterns = [ordered]@{
            muscle_up = 'muscle.?up'
            front_lever = 'front_lever'
            back_lever = 'back_lever'
            dragon_flag = 'dragon'
            planche = 'planche'
        }
        foreach ($goalName in $goalPatterns.Keys) {
            if ($levelTokenText -notmatch $goalPatterns[$goalName]) {
                Add-Error "Level $levelNumber does not include the $goalName goal"
            }
        }
    }
}

$catalogHardRun = 0
$catalogHardRunStart = ""
foreach ($orderedLevel in @($levels | Sort-Object { [int]$_.number })) {
    foreach ($orderedProgram in @($orderedLevel.programs | Sort-Object { [int]$_.number })) {
        if ([string]$orderedProgram.origin -eq "apk_authentic") {
            $catalogHardRun = 0
            $catalogHardRunStart = ""
            continue
        }
        if ([int]$orderedProgram.schedule.rpe -ge 7) {
            if ($catalogHardRun -eq 0) {
                $catalogHardRunStart = "Level $([int]$orderedProgram.level) program $([int]$orderedProgram.number)"
            }
            $catalogHardRun++
            if ($catalogHardRun -eq 4) {
                Add-Error "Catalog order has more than 3 consecutive authored RPE 7+ programs, starting at $catalogHardRunStart"
            }
        } else {
            $catalogHardRun = 0
            $catalogHardRunStart = ""
        }
    }
}

# Skill regressions must be rehearsed before they are needed as fallbacks. The
# app is one fixed global sequence, so an earlier level counts as prior work and
# an earlier warm-up/main step in the same workout counts as an introduction.
$rehearsedMovementIds = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
$rehearsalRequiredCategories = @(
    "skill_foundation",
    "skill",
    "skill_strength",
    "advanced_skill",
    "advanced_strength",
    "mastery_skill"
)
foreach ($orderedLevel in @($levels | Sort-Object { [int]$_.number })) {
    foreach ($orderedProgram in @($orderedLevel.programs | Sort-Object { [int]$_.number })) {
        if ([string]$orderedProgram.origin -eq "apk_authentic") { continue }
        foreach ($sectionName in @("warmup", "main")) {
            $orderedSteps = @((Get-PropertyValue $orderedProgram $sectionName))
            for ($orderedStepIndex = 0; $orderedStepIndex -lt $orderedSteps.Count; $orderedStepIndex++) {
                $orderedValues = @($orderedSteps[$orderedStepIndex])
                if ($orderedValues.Count -ne 7) { continue }
                $movementId = [string]$orderedValues[0]
                $regressionId = [string]$orderedValues[6]
                if ((Has-Text $regressionId) -and $movementById.ContainsKey($regressionId) -and
                    $rehearsalRequiredCategories -contains [string]$movementById[$regressionId].category -and
                    -not $rehearsedMovementIds.Contains($regressionId)) {
                    Add-Error "Level $([int]$orderedProgram.level) program $([int]$orderedProgram.number) $sectionName $($orderedStepIndex + 1) uses skill regression '$regressionId' before it is rehearsed in the guided sequence"
                }
                if (Has-Text $movementId) { [void]$rehearsedMovementIds.Add($movementId) }
            }
        }
    }
}

foreach ($requiredLevel in 1..6) {
    if (-not $seenLevels.Contains($requiredLevel)) { Add-Error "Missing Level $requiredLevel" }
}
if ($totalPrograms -ne 600) { Add-Error "Expected 600 total programs, found $totalPrograms" }
if ($programIds.Count -ne 600) { Add-Error "Expected 600 unique program ids, found $($programIds.Count)" }
if ([int]$originCounts.apk_authentic -ne 90) {
    Add-Error "Expected 90 apk_authentic programs, found $($originCounts.apk_authentic)"
}
if ([int]$originCounts.reconstructed_placeholder -ne 180) {
    Add-Error "Expected 180 reconstructed_placeholder programs, found $($originCounts.reconstructed_placeholder)"
}
if ([int]$originCounts.llm_extension -ne 30) {
    Add-Error "Expected 30 llm_extension programs, found $($originCounts.llm_extension)"
}
if ([int]$originCounts.new_level -ne 300) {
    Add-Error "Expected 300 new_level programs, found $($originCounts.new_level)"
}

$expectedSummary = [ordered]@{
    level_count = 6
    program_count = 600
    authentic_program_count = 90
    reconstructed_placeholder_count = 180
    new_extension_program_count = 30
    new_level_program_count = 300
    archived_legacy_placeholder_count = 109
    movement_count = $movementById.Count
}
foreach ($summaryName in $expectedSummary.Keys) {
    $summaryValue = [long]0
    if (-not (Try-GetInteger (Get-PropertyValue $catalog.summary $summaryName) ([ref]$summaryValue)) -or
        $summaryValue -ne [long]$expectedSummary[$summaryName]) {
        Add-Error "summary.$summaryName must be $($expectedSummary[$summaryName])"
    }
}

if (Test-Path -LiteralPath $AppSourcePath -PathType Container) {
    $textExtensions = @('.xml', '.json', '.txt', '.kt', '.java', '.properties', '.gradle', '.kts', '.html', '.js')
    foreach ($item in @(Get-ChildItem -LiteralPath $AppSourcePath -Recurse -Force)) {
        if ($item.FullName -match '(?i)(language_polish|polski)') {
            Add-Error "App source path contains a forbidden Polish-language marker: $($item.FullName)"
        }
        if ($item.PSIsContainer -or $textExtensions -notcontains $item.Extension.ToLowerInvariant()) { continue }
        try {
            $sourceText = Get-Content -Raw -LiteralPath $item.FullName
            if ($sourceText -match '(?i)(language_polish|polski)') {
                Add-Error "App source file contains a forbidden Polish-language marker: $($item.FullName)"
            }
        } catch {
            Add-Error "Could not inspect app source file $($item.FullName): $($_.Exception.Message)"
        }
    }
} else {
    Add-Warning "App source path was not found; Polish-language resource scan was skipped: $AppSourcePath"
}

if (Has-Text $PSCommandPath) {
    $nonAsciiBytes = @([System.IO.File]::ReadAllBytes($PSCommandPath) | Where-Object { $_ -gt 127 })
    if ($nonAsciiBytes.Count -gt 0) { Add-Error "Validator source is not ASCII-only" }
}

Write-Host "Validated catalog: $CatalogPath"
Write-Host "Levels: $($levels.Count); programs: $totalPrograms; movements: $($movementById.Count)"
Write-Host ("Origins: authentic={0}; reconstructed={1}; extension={2}; new-level={3}" -f
    $originCounts.apk_authentic,
    $originCounts.reconstructed_placeholder,
    $originCounts.llm_extension,
    $originCounts.new_level)

foreach ($warning in $warnings) { Write-Warning $warning }
if ($errors.Count -gt 0) {
    Write-Host ""
    Write-Host "VALIDATION FAILED with $($errors.Count) error(s):" -ForegroundColor Red
    foreach ($validationError in $errors) { Write-Host " - $validationError" -ForegroundColor Red }
    exit 1
}

Write-Host "VALIDATION PASSED" -ForegroundColor Green
exit 0
