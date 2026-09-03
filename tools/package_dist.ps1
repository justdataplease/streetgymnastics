[CmdletBinding()]
param(
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot

try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "tools\assemble_curriculum.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Curriculum assembly failed" }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "tools\validate_curriculum.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Curriculum validation failed" }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "tools\validate_animations.ps1"
    if ($LASTEXITCODE -ne 0) { throw "Exercise animation validation failed" }

    New-Item -ItemType Directory -Force -Path "app\src\main\assets" | Out-Null
    Copy-Item -Force -LiteralPath "generated\curriculum_v2.json" -Destination "app\src\main\assets\curriculum.json"

    $env:JAVA_HOME = $JavaHome
    & ".\gradlew.bat" assembleDebug lintDebug testDebugUnitTest --offline --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Android build or lint failed" }

    New-Item -ItemType Directory -Force -Path "dist" | Out-Null
    Remove-Item -Force -ErrorAction SilentlyContinue -LiteralPath "dist\street-gymnastic-revival-debug.apk"
    Copy-Item -Force -LiteralPath "app\build\outputs\apk\debug\app-debug.apk" -Destination "dist\street-gymnastic-debug.apk"
    Copy-Item -Force -LiteralPath "generated\curriculum_v2.json" -Destination "dist\street-gymnastic-curriculum-v2.json"
    Copy-Item -Force -LiteralPath "app\src\main\assets\exercise_animations.json" -Destination "dist\street-gymnastic-exercise-animations.json"
    Copy-Item -Force -LiteralPath "street_gymnastic_programs_levels.json" -Destination "dist\street-gymnastic-forensic-export.json"
    Copy-Item -Force -LiteralPath "docs\EXTRACTION_REPORT.md" -Destination "dist\EXTRACTION_REPORT.md"
    Copy-Item -Force -LiteralPath "docs\CURRICULUM_METHOD.md" -Destination "dist\CURRICULUM_METHOD.md"
    Copy-Item -Force -LiteralPath "packaging\DIST_README.md" -Destination "dist\README.md"

    $distributionFiles = @(
        "dist\street-gymnastic-debug.apk",
        "dist\street-gymnastic-curriculum-v2.json",
        "dist\street-gymnastic-exercise-animations.json",
        "dist\street-gymnastic-forensic-export.json",
        "dist\EXTRACTION_REPORT.md",
        "dist\CURRICULUM_METHOD.md",
        "dist\README.md"
    )
    $expectedDistributionNames = @(
        $distributionFiles | ForEach-Object { Split-Path -Leaf $_ }
    ) + @("SHA256SUMS.txt")
    $unexpectedDistributionItems = @(
        Get-ChildItem -LiteralPath "dist" -Force | Where-Object {
            $_.Name -notin $expectedDistributionNames
        }
    )
    if ($unexpectedDistributionItems.Count -gt 0) {
        $unexpectedNames = @($unexpectedDistributionItems | ForEach-Object { $_.Name }) -join ", "
        throw "Distribution contains unexpected unchecksummed items: $unexpectedNames"
    }
    $checksumLines = foreach ($file in $distributionFiles) {
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant()
        "$hash *$(Split-Path -Leaf $file)"
    }
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines(
        (Join-Path $projectRoot "dist\SHA256SUMS.txt"),
        $checksumLines,
        $utf8WithoutBom
    )

    $apk = Get-Item -LiteralPath "dist\street-gymnastic-debug.apk"
    Write-Host "Distribution ready in $projectRoot\dist"
    Write-Host "Debug APK size: $($apk.Length) bytes"
}
finally {
    Pop-Location
}
