[CmdletBinding()]
param()
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$plan = Get-Content -Raw -Encoding UTF8 (Join-Path $root "app/src/main/assets/basketball_curriculum.json") | ConvertFrom-Json
function Assert-Plan([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
Assert-Plan ($plan.levels.Count -eq 6) "Expected six basketball levels"
$movements = @{}
foreach ($movement in $plan.movement_library) {
    Assert-Plan (-not $movements.ContainsKey($movement.id)) "Duplicate movement $($movement.id)"
    Assert-Plan ($movement.id -like "bb_*") "Missing basketball movement namespace"
    Assert-Plan (-not [string]::IsNullOrWhiteSpace($movement.description)) "Missing drill instructions"
    Assert-Plan ($movement.equipment.Count -gt 0) "Missing equipment for $($movement.id)"
    $movements[$movement.id] = $movement
}
$programIds = @{}
$usedMovements = @{}
$categories = @{}
$expectedLevel = 1
foreach ($level in $plan.levels) {
    Assert-Plan ($level.number -eq $expectedLevel) "Level order mismatch"
    Assert-Plan ($level.programs.Count -eq 8) "Expected eight sessions in level $expectedLevel"
    $expectedSession = 1
    foreach ($program in $level.programs) {
        Assert-Plan ($program.id -like "bb:*") "Missing basketball program namespace"
        Assert-Plan (-not $programIds.ContainsKey($program.id)) "Duplicate program $($program.id)"
        $programIds[$program.id] = $true
        Assert-Plan ($program.number -eq $expectedSession) "Session order mismatch in $($program.id)"
        Assert-Plan ($program.target_rpe -ge 3 -and $program.target_rpe -le 6) "Unexpected effort in $($program.id)"
        Assert-Plan ($program.estimated_minutes -ge 15 -and $program.estimated_minutes -le 45) "Invalid session estimate"
        Assert-Plan (-not [string]::IsNullOrWhiteSpace($program.readiness)) "Missing readiness"
        Assert-Plan (-not [string]::IsNullOrWhiteSpace($program.safety)) "Missing safety"
        foreach ($section in @("warmup", "main", "cooldown")) {
            Assert-Plan ($program.$section.Count -gt 0) "Missing $section in $($program.id)"
            foreach ($dose in $program.$section) {
                Assert-Plan ($movements.ContainsKey($dose[0])) "Unknown movement $($dose[0])"
                Assert-Plan ($dose[1] -ge 1 -and $dose[1] -le 3) "Invalid set count"
                Assert-Plan ($null -ne $dose[2] -or $null -ne $dose[3]) "Missing dose"
                Assert-Plan ($dose[2] -ne "max") "Uncapped repetitions"
                if ($null -ne $dose[3]) { Assert-Plan ($dose[3] -gt 0 -and $dose[3] -le 300) "Invalid timed dose" }
                Assert-Plan ($dose[4] -ge 0 -and $dose[4] -le 120) "Invalid rest"
                $usedMovements[$dose[0]] = $true
                $categories[$movements[$dose[0]].category] = $true
            }
        }
        if ($expectedSession -eq 4) {
            Assert-Plan ($program.day_type -eq "recovery" -and $program.target_rpe -le 3) "Missing easy practice"
        }
        if ($expectedSession -eq 8) {
            Assert-Plan ($program.day_type -eq "checkpoint" -and $program.target_rpe -le 4) "Missing checkpoint"
        }
        $expectedSession++
    }
    $expectedLevel++
}
Assert-Plan ($programIds.Count -eq 48) "Expected 48 unique basketball sessions"
Assert-Plan ($usedMovements.Count -eq $movements.Count) "Unused movement definitions"
foreach ($category in @("dribbling", "shooting", "finishing", "passing", "footwork", "defense", "decision", "recovery")) {
    Assert-Plan ($categories.ContainsKey($category)) "Missing skill category $category"
}
Write-Host "Basketball validation passed: 6 levels, 48 sessions, $($movements.Count) drills, all doses and references valid."
