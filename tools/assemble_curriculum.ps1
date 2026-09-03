[CmdletBinding()]
param(
    [string]$BaselinePath = "street_gymnastic_programs_levels.json",
    [string]$CurriculumDirectory = "curriculum",
    [string]$OutputPath = "generated/curriculum_v2.json"
)

$ErrorActionPreference = "Stop"
$expectedBaselineSha256 = "AC8AAF41F477050EDDF9387DF1CD214FCBE3A29608AE4D5AE1E0C34D9F5EDA3D"
$targetProgramsPerLevel = 100

function Read-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required JSON file not found: $Path" }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

function Stable-ProgramId([int]$Level, [int]$Number) {
    return "sg:l{0:D2}:p{1:D4}" -f $Level, $Number
}

function Localized([string]$English) {
    return [ordered]@{ en = $English }
}

$script:polishKey = ([string][char]112) + ([string][char]108)
function Remove-PolishFields($Value) {
    if ($null -eq $Value -or $Value -is [string]) { return }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        if ($null -ne $Value.PSObject.Properties[$script:polishKey]) {
            $Value.PSObject.Properties.Remove($script:polishKey)
        }
        foreach ($property in @($Value.PSObject.Properties)) {
            Remove-PolishFields $property.Value
        }
        return
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) { Remove-PolishFields $item }
    }
}

$baselineHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaselinePath).Hash
if ($baselineHash -ne $expectedBaselineSha256) {
    throw "Baseline SHA-256 mismatch. Expected $expectedBaselineSha256, got $baselineHash"
}

$baseline = Read-JsonFile $BaselinePath
$catalogMetadata = Read-JsonFile (Join-Path $CurriculumDirectory "catalog_metadata.json")
$authoredBySlot = @{}
$movementById = [ordered]@{}
$coachingProfiles = New-Object System.Collections.ArrayList
$batchFiles = Get-ChildItem -LiteralPath $CurriculumDirectory -Filter "*.json" -File | Sort-Object Name

foreach ($file in $batchFiles) {
    $batch = Read-JsonFile $file.FullName
    foreach ($movement in @($batch.movements)) {
        if ($null -eq $movement) { continue }
        $id = [string]$movement.id
        if ([string]::IsNullOrWhiteSpace($id)) { throw "Movement without an id in $($file.Name)" }
        if ($movementById.Contains($id)) { throw "Duplicate movement id $id in $($file.Name)" }
        $movementById[$id] = $movement
    }
    foreach ($profile in @($batch.coaching_profiles)) {
        if ($null -ne $profile) { [void]$coachingProfiles.Add($profile) }
    }
    foreach ($program in @($batch.programs)) {
        if ($null -eq $program) { continue }
        $level = [int]$program.level
        $number = [int]$program.number
        $slot = "$level-$number"
        if ($authoredBySlot.ContainsKey($slot)) { throw "Duplicate authored program slot $slot in $($file.Name)" }
        $authoredBySlot[$slot] = $program
    }
}

if ($movementById.Count -eq 0) { throw "No movement definitions were found" }
if ($coachingProfiles.Count -eq 0) { throw "No coaching profiles were found" }

$levelDefinitions = @($catalogMetadata.level_definitions)
$assembledLevels = New-Object System.Collections.ArrayList
$authenticCount = 0
$reconstructedCount = 0
$newLevelCount = 0
$newExtensionCount = 0
$archivedLegacyPlaceholderCount = 0

foreach ($levelDefinition in $levelDefinitions) {
    $levelNumber = [int]$levelDefinition.number
    $programs = New-Object System.Collections.ArrayList

    if ($levelNumber -le 3) {
        $baselineLevel = $baseline.levels | Where-Object { [int]$_.number -eq $levelNumber } | Select-Object -First 1
        if ($null -eq $baselineLevel) { throw "Baseline level $levelNumber is missing" }

        $archivedLegacyPrograms = @($baselineLevel.programs | Where-Object {
            [int]$_.number -gt $targetProgramsPerLevel
        })
        if (@($archivedLegacyPrograms | Where-Object { [bool]$_.has_details }).Count -gt 0) {
            throw "Level $levelNumber would archive authentic APK content above slot $targetProgramsPerLevel"
        }
        $archivedLegacyPlaceholderCount += $archivedLegacyPrograms.Count
        $includedBaselinePrograms = @($baselineLevel.programs | Where-Object {
            [int]$_.number -le $targetProgramsPerLevel
        } | Sort-Object { [int]$_.number })

        foreach ($legacyProgram in $includedBaselinePrograms) {
            $number = [int]$legacyProgram.number
            $stableId = Stable-ProgramId $levelNumber $number

            if ([bool]$legacyProgram.has_details) {
                Remove-PolishFields $legacyProgram
                $profile = $coachingProfiles[($number - 1) % $coachingProfiles.Count]
                $assembled = [ordered]@{
                    id = $stableId
                    origin = "apk_authentic"
                    level = $levelNumber
                    number = $number
                    title = Localized "$($catalogMetadata.authentic_title_prefix.en) $number"
                    description = $catalogMetadata.authentic_description
                    schedule = [ordered]@{
                        week = [Math]::Floor(($number - 1) / 7) + 1
                        day = (($number - 1) % 7) + 1
                        day_type = "authentic_base"
                        minutes = $null
                        rpe = $null
                        focus = @("authentic_base")
                    }
                    legacy = [ordered]@{
                        workout_id = [string]$legacyProgram.id
                        pro = [bool]$legacyProgram.pro
                        has_details = $true
                    }
                    content = $legacyProgram
                    coaching_overlay = [ordered]@{
                        origin = "new_coaching_overlay"
                        profile_id = [string]$profile.id
                        warmup = $profile.warmup
                        cooldown = $profile.cooldown
                    }
                }
                [void]$programs.Add($assembled)
                $authenticCount++
                continue
            }

            $slot = "$levelNumber-$number"
            if (-not $authoredBySlot.ContainsKey($slot)) {
                throw "No LLM-authored replacement exists for legacy placeholder $slot"
            }
            $authored = $authoredBySlot[$slot]
            $assembled = [ordered]@{
                id = $stableId
                authored_id = [string]$authored.id
                origin = "reconstructed_placeholder"
                level = $levelNumber
                number = $number
                title = $authored.title
                description = $authored.description
                schedule = $authored.schedule
                legacy = [ordered]@{
                    workout_id = [string]$legacyProgram.id
                    pro = [bool]$legacyProgram.pro
                    has_details = $false
                }
                warmup = $authored.warmup
                main = $authored.main
                cooldown = $authored.cooldown
                readiness = $authored.readiness
                safety = $authored.safety
            }
            [void]$programs.Add($assembled)
            $reconstructedCount++
        }

        $includedBaselineMaximum = @(
            $includedBaselinePrograms |
                ForEach-Object { [int]$_.number } |
                Measure-Object -Maximum
        )[0].Maximum
        $extensionPrograms = @($authoredBySlot.Values | Where-Object {
            [int]$_.level -eq $levelNumber -and
            [int]$_.number -gt $includedBaselineMaximum -and
            [int]$_.number -le $targetProgramsPerLevel
        } | Sort-Object { [int]$_.number })
        foreach ($authored in $extensionPrograms) {
            $number = [int]$authored.number
            $assembled = [ordered]@{
                id = Stable-ProgramId $levelNumber $number
                authored_id = [string]$authored.id
                origin = "llm_extension"
                level = $levelNumber
                number = $number
                title = $authored.title
                description = $authored.description
                schedule = $authored.schedule
                warmup = $authored.warmup
                main = $authored.main
                cooldown = $authored.cooldown
                readiness = $authored.readiness
                safety = $authored.safety
            }
            [void]$programs.Add($assembled)
            $newExtensionCount++
        }
    }
    else {
        $authoredPrograms = @($authoredBySlot.Values | Where-Object {
            [int]$_.level -eq $levelNumber -and [int]$_.number -le $targetProgramsPerLevel
        } | Sort-Object { [int]$_.number })
        if ($authoredPrograms.Count -eq 0) { throw "No LLM-authored programs exist for new level $levelNumber" }
        foreach ($authored in $authoredPrograms) {
            $number = [int]$authored.number
            $assembled = [ordered]@{
                id = Stable-ProgramId $levelNumber $number
                authored_id = [string]$authored.id
                origin = "new_level"
                level = $levelNumber
                number = $number
                title = $authored.title
                description = $authored.description
                schedule = $authored.schedule
                warmup = $authored.warmup
                main = $authored.main
                cooldown = $authored.cooldown
                readiness = $authored.readiness
                safety = $authored.safety
            }
            [void]$programs.Add($assembled)
            $newLevelCount++
        }
    }

    if ($programs.Count -ne $targetProgramsPerLevel) {
        throw "Level $levelNumber must contain exactly $targetProgramsPerLevel app workouts; found $($programs.Count)"
    }

    [void]$assembledLevels.Add([ordered]@{
        id = "sg:l{0:D2}" -f $levelNumber
        number = $levelNumber
        name = $levelDefinition.name
        description = $levelDefinition.description
        programs = $programs
    })
}

$result = [ordered]@{
    schema_version = "2.0.0"
    source_baseline = [ordered]@{
        file = (Split-Path -Leaf $BaselinePath)
        sha256 = $baselineHash
        immutable = $true
        apk_sha256 = [string]$baseline.source.apk_sha256
        package_name = [string]$baseline.source.package_name
        version_name = [string]$baseline.source.version_name
        authentic_program_count = 90
        placeholder_count = 289
        included_placeholder_count = $reconstructedCount
        archived_placeholder_count = $archivedLegacyPlaceholderCount
        app_slot_limit_per_level = $targetProgramsPerLevel
    }
    catalog_provenance = [ordered]@{
        classification = "llm_authored_reconstructed_curriculum"
        notice = $catalogMetadata.catalog_notice
        content_generation = "Static sessions authored by language-model review; scripts only assemble and validate."
        content_revision = 2
    }
    summary = [ordered]@{
        level_count = $assembledLevels.Count
        program_count = $authenticCount + $reconstructedCount + $newExtensionCount + $newLevelCount
        authentic_program_count = $authenticCount
        reconstructed_placeholder_count = $reconstructedCount
        new_extension_program_count = $newExtensionCount
        new_level_program_count = $newLevelCount
        archived_legacy_placeholder_count = $archivedLegacyPlaceholderCount
        movement_count = $movementById.Count
    }
    step_tuple = @("movement_id", "sets", "reps", "seconds", "rest_seconds", "tempo", "regression_id")
    movement_library = @($movementById.Values)
    coaching_profiles = @($coachingProfiles)
    levels = $assembledLevels
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
$json = $result | ConvertTo-Json -Depth 100 -Compress
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutputPath), $json + [Environment]::NewLine, $utf8WithoutBom)

Write-Host "Assembled $($result.summary.program_count) programs across $($result.summary.level_count) levels."
Write-Host "Authentic: $authenticCount; reconstructed placeholders: $reconstructedCount; new-level: $newLevelCount."
Write-Host "LLM extensions: $newExtensionCount; archived legacy placeholders retained in forensic export: $archivedLegacyPlaceholderCount."
Write-Host "Movement definitions: $($movementById.Count); coaching profiles: $($coachingProfiles.Count)."
Write-Host "Output: $OutputPath"
