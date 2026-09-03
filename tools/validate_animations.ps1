[CmdletBinding()]
param(
    [string]$AnimationPath = "app\src\main\assets\exercise_animations.json",
    [string]$MovementLibraryPath = "curriculum\movement_library.json",
    [string]$ForensicExportPath = "street_gymnastic_programs_levels.json"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$errors = New-Object System.Collections.Generic.List[string]

function Add-ValidationError {
    param([string]$Message)
    $script:errors.Add($Message)
}

function Resolve-ProjectPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $projectRoot $Path
}

function Read-JsonFile {
    param(
        [string]$Path,
        [string]$Label
    )
    $resolved = Resolve-ProjectPath $Path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        Add-ValidationError "$Label is missing: $resolved"
        return $null
    }
    try {
        return Get-Content -Raw -LiteralPath $resolved -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        Add-ValidationError "$Label is not valid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Has-Property {
    param(
        [AllowNull()]$Object,
        [string]$Name
    )
    if ($null -eq $Object) { return $false }
    return $null -ne $Object.PSObject.Properties[$Name]
}

function Get-Properties {
    param([AllowNull()]$Object)
    if ($null -eq $Object) { return @() }
    return @($Object.PSObject.Properties)
}

function Test-JsonObject {
    param([AllowNull()]$Value)
    return $null -ne $Value -and $Value -is [pscustomobject]
}

function Test-JsonArray {
    param([AllowNull()]$Value)
    return $null -ne $Value -and $Value -is [System.Array]
}

function Test-JsonNumber {
    param([AllowNull()]$Value)
    if ($null -eq $Value -or $Value -is [bool]) { return $false }
    return $Value -is [byte] -or
        $Value -is [sbyte] -or
        $Value -is [int16] -or
        $Value -is [uint16] -or
        $Value -is [int32] -or
        $Value -is [uint32] -or
        $Value -is [int64] -or
        $Value -is [uint64] -or
        $Value -is [single] -or
        $Value -is [double] -or
        $Value -is [decimal]
}

function Test-FiniteNumber {
    param([AllowNull()]$Value)
    if (-not (Test-JsonNumber $Value)) { return $false }
    $number = [double]$Value
    return -not [double]::IsNaN($number) -and -not [double]::IsInfinity($number)
}

function Normalize-Key {
    param([AllowNull()][string]$Value)
    if ($null -eq $Value) { return "" }
    return (($Value.ToLowerInvariant() -replace '[^a-z0-9]+', '_').Trim('_'))
}

function Test-JointArray {
    param(
        [AllowNull()]$Joints,
        [string]$Location
    )
    if (-not (Test-JsonArray $Joints) -or @($Joints).Count -ne 11) {
        Add-ValidationError "$Location must contain exactly 11 coordinate pairs (22 values)"
        return $false
    }
    for ($jointIndex = 0; $jointIndex -lt 11; $jointIndex++) {
        $joint = @($Joints)[$jointIndex]
        if (-not (Test-JsonArray $joint) -or @($joint).Count -ne 2) {
            Add-ValidationError "$Location[$jointIndex] must contain exactly two coordinates"
            continue
        }
        for ($coordinateIndex = 0; $coordinateIndex -lt 2; $coordinateIndex++) {
            $coordinate = @($joint)[$coordinateIndex]
            if (-not (Test-FiniteNumber $coordinate)) {
                Add-ValidationError "$Location[$jointIndex][$coordinateIndex] must be finite numeric data"
            }
            elseif ([double]$coordinate -lt 0.0 -or [double]$coordinate -gt 1.0) {
                Add-ValidationError "$Location[$jointIndex][$coordinateIndex] must be normalized from 0 through 1"
            }
        }
    }
    return $true
}

function Test-Reference {
    param(
        [AllowNull()]$Reference,
        [string]$Location,
        [hashtable]$TemplateNames,
        [hashtable]$ApparatusNames
    )

    $templateName = $null
    $apparatusOverride = $null
    if ($Reference -is [string]) {
        $templateName = [string]$Reference
    }
    elseif (Test-JsonObject $Reference) {
        if (-not (Has-Property $Reference "template") -or -not ($Reference.template -is [string])) {
            Add-ValidationError "$Location must contain a string template"
            return
        }
        $templateName = [string]$Reference.template
        if (Has-Property $Reference "apparatus") {
            if (-not (Test-JsonArray $Reference.apparatus)) {
                Add-ValidationError "$Location.apparatus must be an array"
            }
            else {
                $apparatusOverride = @($Reference.apparatus)
            }
        }
    }
    else {
        Add-ValidationError "$Location must be a template name or reference object"
        return
    }

    if ([string]::IsNullOrWhiteSpace($templateName)) {
        Add-ValidationError "$Location template must not be blank"
    }
    elseif (-not $TemplateNames.ContainsKey($templateName)) {
        Add-ValidationError "$Location references unknown template '$templateName'"
    }

    if ($null -ne $apparatusOverride) {
        $seen = @{}
        for ($index = 0; $index -lt $apparatusOverride.Count; $index++) {
            $name = $apparatusOverride[$index]
            if (-not ($name -is [string]) -or [string]::IsNullOrWhiteSpace([string]$name)) {
                Add-ValidationError "$Location.apparatus[$index] must be a non-blank string"
                continue
            }
            if ($seen.ContainsKey([string]$name)) {
                Add-ValidationError "$Location.apparatus repeats '$name'"
            }
            $seen[[string]$name] = $true
            if (-not $ApparatusNames.ContainsKey([string]$name)) {
                Add-ValidationError "$Location.apparatus references unknown apparatus '$name'"
            }
        }
    }
}

$animationResolved = Resolve-ProjectPath $AnimationPath
if (Test-Path -LiteralPath $animationResolved -PathType Leaf) {
    $rawAnimation = Get-Content -Raw -LiteralPath $animationResolved -Encoding UTF8
    if ($rawAnimation -match '(?i)https?://|\bvimeo\b|\byoutu(?:be)?\b|"[^"\r\n]*(?:video|video_id|video_url)[^"\r\n]*"\s*:') {
        Add-ValidationError "Animation JSON must not contain video URLs, providers, or video-ID fields"
    }
}

$animation = Read-JsonFile $AnimationPath "Animation asset"
$movementLibrary = Read-JsonFile $MovementLibraryPath "Movement library"
$forensicExport = Read-JsonFile $ForensicExportPath "Forensic export"

if ($null -ne $animation) {
    if (-not (Test-JsonObject $animation)) {
        Add-ValidationError "Animation asset root must be an object"
    }

    if (-not (Has-Property $animation "schema_version") -or -not (Test-JsonNumber $animation.schema_version) -or
        [double]$animation.schema_version -ne 1.0) {
        Add-ValidationError "Animation asset .schema_version must be the supported value 1"
    }
    if (-not (Has-Property $animation "coordinate_system") -or [string]$animation.coordinate_system -cne "normalized_top_left") {
        Add-ValidationError "Animation asset .coordinate_system must be 'normalized_top_left'"
    }
    $expectedJointOrder = @(
        "head", "neck", "hip", "left_elbow", "left_hand", "right_elbow",
        "right_hand", "left_knee", "left_foot", "right_knee", "right_foot"
    )
    if (-not (Has-Property $animation "joint_order") -or -not (Test-JsonArray $animation.joint_order) -or
        (@($animation.joint_order) -join "|") -cne ($expectedJointOrder -join "|")) {
        Add-ValidationError "Animation asset .joint_order must match the renderer's 11-joint order"
    }

    $requiredRootObjects = @("palette", "apparatus", "poses", "templates", "movements", "aliases", "category_defaults", "equipment_defaults")
    foreach ($field in $requiredRootObjects) {
        if (-not (Has-Property $animation $field) -or -not (Test-JsonObject $animation.$field)) {
            Add-ValidationError "Animation asset .$field must be an object"
        }
    }
    if (-not (Has-Property $animation "name_heuristics") -or -not (Test-JsonArray $animation.name_heuristics)) {
        Add-ValidationError "Animation asset .name_heuristics must be an array"
    }
    if (-not (Has-Property $animation "fallback") -or -not ($animation.fallback -is [string]) -or [string]::IsNullOrWhiteSpace([string]$animation.fallback)) {
        Add-ValidationError "Animation asset .fallback must be a non-blank template name"
    }

    if (Test-JsonObject $animation.palette) {
        foreach ($key in @("background", "border", "figure", "accent", "apparatus", "floor")) {
            if (-not (Has-Property $animation.palette $key) -or
                -not ($animation.palette.$key -is [string]) -or
                ([string]$animation.palette.$key -notmatch '^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$')) {
                Add-ValidationError "palette.$key must be a #RRGGBB or #AARRGGBB color"
            }
        }
    }

    $apparatusNames = @{}
    if (Test-JsonObject $animation.apparatus) {
        foreach ($property in Get-Properties $animation.apparatus) {
            $name = [string]$property.Name
            $apparatusNames[$name] = $true
            $primitives = $property.Value
            if (-not (Test-JsonArray $primitives) -or @($primitives).Count -eq 0) {
                Add-ValidationError "apparatus.$name must be a non-empty array"
                continue
            }
            for ($primitiveIndex = 0; $primitiveIndex -lt @($primitives).Count; $primitiveIndex++) {
                $primitive = @($primitives)[$primitiveIndex]
                $location = "apparatus.$name[$primitiveIndex]"
                if (-not (Test-JsonObject $primitive)) {
                    Add-ValidationError "$location must be an object"
                    continue
                }
                $type = [string]$primitive.type
                $expectedValueCount = switch ($type) {
                    "line" { 4 }
                    "rect" { 4 }
                    "round_rect" { 4 }
                    "circle" { 3 }
                    "polyline" { -1 }
                    default { 0 }
                }
                if ($expectedValueCount -eq 0) {
                    Add-ValidationError "$location.type '$type' is unsupported"
                }
                if ((Has-Property $primitive "role") -and
                    (-not ($primitive.role -is [string]) -or [string]$primitive.role -notin @("apparatus", "floor", "accent"))) {
                    Add-ValidationError "$location.role must be 'apparatus', 'floor', or 'accent'"
                }
                if (-not (Has-Property $primitive "values") -or -not (Test-JsonArray $primitive.values)) {
                    Add-ValidationError "$location.values must be an array"
                    continue
                }
                $values = @($primitive.values)
                if ($expectedValueCount -gt 0 -and $values.Count -ne $expectedValueCount) {
                    Add-ValidationError "$location.values must contain exactly $expectedValueCount numbers"
                }
                if ($expectedValueCount -eq -1 -and ($values.Count -lt 4 -or $values.Count % 2 -ne 0)) {
                    Add-ValidationError "$location.values must contain at least two coordinate pairs"
                }
                for ($valueIndex = 0; $valueIndex -lt $values.Count; $valueIndex++) {
                    $value = $values[$valueIndex]
                    if (-not (Test-FiniteNumber $value)) {
                        Add-ValidationError "$location.values[$valueIndex] must be finite numeric data"
                    }
                    elseif ([double]$value -lt 0.0 -or [double]$value -gt 1.0) {
                        Add-ValidationError "$location.values[$valueIndex] must be normalized from 0 through 1"
                    }
                }
                if ((Has-Property $primitive "filled") -and -not ($primitive.filled -is [bool])) {
                    Add-ValidationError "$location.filled must be boolean"
                }
            }
        }
        if ($apparatusNames.Count -eq 0) { Add-ValidationError "Animation asset must define apparatus" }
    }

    $poseNames = @{}
    if (Test-JsonObject $animation.poses) {
        foreach ($property in Get-Properties $animation.poses) {
            $poseName = [string]$property.Name
            $poseNames[$poseName] = $true
            $pose = $property.Value
            $location = "poses.$poseName"
            if ([string]::IsNullOrWhiteSpace($poseName) -or -not (Test-JsonObject $pose)) {
                Add-ValidationError "$location must be a named object"
                continue
            }
            Test-JointArray $pose.joints "$location.joints" | Out-Null
            if ((Has-Property $pose "spine_curve") -and -not (Test-FiniteNumber $pose.spine_curve)) {
                Add-ValidationError "$location.spine_curve must be finite numeric data"
            }
        }
        if ($poseNames.Count -eq 0) { Add-ValidationError "Animation asset must define at least one reusable pose" }
    }

    $templateNames = @{}
    if (Test-JsonObject $animation.templates) {
        foreach ($property in Get-Properties $animation.templates) { $templateNames[[string]$property.Name] = $true }
        if ($templateNames.Count -eq 0) { Add-ValidationError "Animation asset must define at least one template" }

        foreach ($property in Get-Properties $animation.templates) {
            $name = [string]$property.Name
            $template = $property.Value
            $location = "templates.$name"
            if ([string]::IsNullOrWhiteSpace($name) -or -not (Test-JsonObject $template)) {
                Add-ValidationError "$location must be a named object"
                continue
            }

            if (-not (Has-Property $template "duration_ms") -or -not (Test-FiniteNumber $template.duration_ms) -or
                [double]$template.duration_ms % 1 -ne 0 -or [double]$template.duration_ms -lt 500 -or [double]$template.duration_ms -gt 8000) {
                Add-ValidationError "$location.duration_ms must be an integer from 500 through 8000"
            }
            $mode = [string]$template.mode
            if ($mode -notin @("ping_pong", "loop")) {
                Add-ValidationError "$location.mode must be 'ping_pong' or 'loop'"
            }
            if ((Has-Property $template "easing") -and [string]$template.easing -notin @("smooth", "linear")) {
                Add-ValidationError "$location.easing must be 'smooth' or 'linear'"
            }
            foreach ($boundedField in @("reduced_motion_frame", "head_radius")) {
                if ((Has-Property $template $boundedField) -and -not (Test-FiniteNumber $template.$boundedField)) {
                    Add-ValidationError "$location.$boundedField must be finite numeric data"
                }
            }
            if ((Test-FiniteNumber $template.reduced_motion_frame) -and
                ([double]$template.reduced_motion_frame -lt 0.0 -or [double]$template.reduced_motion_frame -gt 1.0)) {
                Add-ValidationError "$location.reduced_motion_frame must be normalized from 0 through 1"
            }
            if ((Test-FiniteNumber $template.head_radius) -and
                ([double]$template.head_radius -lt 0.02 -or [double]$template.head_radius -gt 0.07)) {
                Add-ValidationError "$location.head_radius must be from 0.02 through 0.07"
            }

            if (-not (Has-Property $template "apparatus") -or -not (Test-JsonArray $template.apparatus)) {
                Add-ValidationError "$location.apparatus must be an array"
            }
            else {
                $seenTemplateApparatus = @{}
                for ($index = 0; $index -lt @($template.apparatus).Count; $index++) {
                    $apparatusName = @($template.apparatus)[$index]
                    if (-not ($apparatusName -is [string]) -or [string]::IsNullOrWhiteSpace([string]$apparatusName)) {
                        Add-ValidationError "$location.apparatus[$index] must be a non-blank string"
                        continue
                    }
                    if ($seenTemplateApparatus.ContainsKey([string]$apparatusName)) {
                        Add-ValidationError "$location.apparatus repeats '$apparatusName'"
                    }
                    $seenTemplateApparatus[[string]$apparatusName] = $true
                    if (-not $apparatusNames.ContainsKey([string]$apparatusName)) {
                        Add-ValidationError "$location.apparatus references unknown apparatus '$apparatusName'"
                    }
                }
            }

            if (-not (Has-Property $template "keyframes") -or -not (Test-JsonArray $template.keyframes) -or @($template.keyframes).Count -lt 1) {
                Add-ValidationError "$location.keyframes must contain at least one frame"
                continue
            }
            $lastAt = -1.0
            for ($frameIndex = 0; $frameIndex -lt @($template.keyframes).Count; $frameIndex++) {
                $frame = @($template.keyframes)[$frameIndex]
                $frameLocation = "$location.keyframes[$frameIndex]"
                if (-not (Test-JsonObject $frame)) {
                    Add-ValidationError "$frameLocation must be an object"
                    continue
                }
                if (-not (Has-Property $frame "at") -or -not (Test-FiniteNumber $frame.at) -or
                    [double]$frame.at -lt 0.0 -or [double]$frame.at -gt 1.0) {
                    Add-ValidationError "$frameLocation.at must be finite and normalized from 0 through 1"
                }
                else {
                    $at = [double]$frame.at
                    if ($at -le $lastAt) { Add-ValidationError "$frameLocation.at must be strictly greater than the preceding keyframe" }
                    $lastAt = $at
                    if ($frameIndex -eq 0 -and $at -ne 0.0) { Add-ValidationError "$location must begin with a keyframe at 0" }
                    if ($frameIndex -eq @($template.keyframes).Count - 1 -and $at -ne 1.0) { Add-ValidationError "$location must end with a keyframe at 1" }
                }
                $hasInlineJoints = Has-Property $frame "joints"
                $hasNamedPose = Has-Property $frame "pose"
                if ($hasNamedPose) {
                    if (-not ($frame.pose -is [string]) -or [string]::IsNullOrWhiteSpace([string]$frame.pose)) {
                        Add-ValidationError "$frameLocation.pose must be a non-blank string"
                    }
                    elseif (-not $poseNames.ContainsKey([string]$frame.pose)) {
                        Add-ValidationError "$frameLocation.pose references unknown pose '$($frame.pose)'"
                    }
                }
                if ($hasInlineJoints) {
                    Test-JointArray $frame.joints "$frameLocation.joints" | Out-Null
                }
                elseif (-not $hasNamedPose) {
                    Add-ValidationError "$frameLocation must contain either pose or joints"
                }
                if ((Has-Property $frame "spine_curve") -and -not (Test-FiniteNumber $frame.spine_curve)) {
                    Add-ValidationError "$frameLocation.spine_curve must be finite numeric data"
                }
            }
        }
    }

    if ((Has-Property $animation "fallback") -and $animation.fallback -is [string] -and
        -not $templateNames.ContainsKey([string]$animation.fallback)) {
        Add-ValidationError "fallback references unknown template '$($animation.fallback)'"
    }

    foreach ($mappingName in @("movements", "aliases", "category_defaults", "equipment_defaults")) {
        $mapping = $animation.$mappingName
        if (-not (Test-JsonObject $mapping)) { continue }
        $normalizedKeys = @{}
        foreach ($property in Get-Properties $mapping) {
            $normalized = Normalize-Key ([string]$property.Name)
            if ([string]::IsNullOrWhiteSpace($normalized)) {
                Add-ValidationError "$mappingName contains a blank normalized key"
            }
            elseif ($normalizedKeys.ContainsKey($normalized)) {
                Add-ValidationError "$mappingName has keys that collide after normalization: '$($property.Name)'"
            }
            else {
                $normalizedKeys[$normalized] = [string]$property.Name
            }
            Test-Reference $property.Value "$mappingName.$($property.Name)" $templateNames $apparatusNames
        }
    }

    if (Test-JsonArray $animation.name_heuristics) {
        for ($index = 0; $index -lt @($animation.name_heuristics).Count; $index++) {
            $heuristic = @($animation.name_heuristics)[$index]
            $location = "name_heuristics[$index]"
            if (-not (Test-JsonObject $heuristic)) {
                Add-ValidationError "$location must be an object"
                continue
            }
            if (-not (Has-Property $heuristic "contains") -or -not (Test-JsonArray $heuristic.contains) -or @($heuristic.contains).Count -eq 0) {
                Add-ValidationError "$location.contains must be a non-empty array"
            }
            else {
                for ($tokenIndex = 0; $tokenIndex -lt @($heuristic.contains).Count; $tokenIndex++) {
                    $token = @($heuristic.contains)[$tokenIndex]
                    if (-not ($token -is [string]) -or [string]::IsNullOrWhiteSpace((Normalize-Key ([string]$token)))) {
                        Add-ValidationError "$location.contains[$tokenIndex] must be a meaningful string"
                    }
                }
            }
            if (-not (Has-Property $heuristic "animation")) {
                Add-ValidationError "$location.animation is required"
            }
            else {
                Test-Reference $heuristic.animation "$location.animation" $templateNames $apparatusNames
            }
        }
    }

    if ($null -ne $movementLibrary -and (Test-JsonArray $movementLibrary.movements) -and (Test-JsonObject $animation.movements)) {
        $expectedMovementIds = @($movementLibrary.movements | ForEach-Object { [string]$_.id })
        if ($expectedMovementIds.Count -ne 115) {
            Add-ValidationError "Authoritative movement library must contain exactly 115 movements, found $($expectedMovementIds.Count)"
        }
        $duplicateExpectedIds = @($expectedMovementIds | Group-Object | Where-Object Count -gt 1)
        foreach ($duplicate in $duplicateExpectedIds) {
            Add-ValidationError "Movement library repeats ID '$($duplicate.Name)'"
        }
        $actualMovementIds = @((Get-Properties $animation.movements) | ForEach-Object { [string]$_.Name })
        foreach ($missing in @($expectedMovementIds | Where-Object { $_ -notin $actualMovementIds })) {
            Add-ValidationError "movements is missing authoritative ID '$missing'"
        }
        foreach ($extra in @($actualMovementIds | Where-Object { $_ -notin $expectedMovementIds })) {
            Add-ValidationError "movements contains unknown ID '$extra'"
        }
        if ($actualMovementIds.Count -ne 115) {
            Add-ValidationError "Animation asset must map exactly 115 movement IDs, found $($actualMovementIds.Count)"
        }
    }
    elseif ($null -ne $movementLibrary) {
        Add-ValidationError "Cannot verify exact movement coverage"
    }

    if ($null -ne $forensicExport -and (Test-JsonObject $animation.aliases)) {
        $authenticNames = @(
            $forensicExport.levels.programs |
                Where-Object { [bool]$_.has_details } |
                ForEach-Object { @($_.exercises) } |
                ForEach-Object { [string]$_.name.en } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                Sort-Object -Unique
        )
        if ($authenticNames.Count -ne 34) {
            Add-ValidationError "Forensic export must expose exactly 34 distinct authentic English exercise names, found $($authenticNames.Count)"
        }
        $aliasKeys = @{}
        foreach ($property in Get-Properties $animation.aliases) {
            $aliasKeys[(Normalize-Key ([string]$property.Name))] = $true
        }
        foreach ($authenticName in $authenticNames) {
            if ($authenticName -ceq "Compound Exercise") { continue }
            $normalizedName = Normalize-Key $authenticName
            if (-not $aliasKeys.ContainsKey($normalizedName)) {
                Add-ValidationError "aliases is missing authentic exercise name '$authenticName' ('$normalizedName')"
            }
        }
    }
    elseif ($null -ne $forensicExport) {
        Add-ValidationError "Cannot verify authentic exercise-name aliases"
    }
}

if ($errors.Count -gt 0) {
    Write-Host "Animation validation failed with $($errors.Count) error(s):" -ForegroundColor Red
    foreach ($validationError in $errors) { Write-Host " - $validationError" -ForegroundColor Red }
    exit 1
}

$templateCount = if ($null -ne $animation -and (Test-JsonObject $animation.templates)) { (Get-Properties $animation.templates).Count } else { 0 }
$poseCount = if ($null -ne $animation -and (Test-JsonObject $animation.poses)) { (Get-Properties $animation.poses).Count } else { 0 }
$movementCount = if ($null -ne $animation -and (Test-JsonObject $animation.movements)) { (Get-Properties $animation.movements).Count } else { 0 }
$aliasCount = if ($null -ne $animation -and (Test-JsonObject $animation.aliases)) { (Get-Properties $animation.aliases).Count } else { 0 }
Write-Host "Animation validation passed: $poseCount poses, $templateCount templates, $movementCount movement mappings, $aliasCount aliases; offline JSON contains no video references."
