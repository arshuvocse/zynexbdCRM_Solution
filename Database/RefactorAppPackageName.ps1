# ==============================================================================
# SCRIPT TO REFACTOR ANDROID APP PACKAGE NAME TO com.zynexbd.crmsolution
# ==============================================================================

$ErrorActionPreference = "Stop"
$appDir = "D:\Shuvo\zynexbd\CRM_Solution\CRM_Apps\app"

Write-Host "1. Moving source directories..." -ForegroundColor Yellow

$mainOldDir = "$appDir\src\main\java\com\zynexbd\livetracking"
$mainNewDir = "$appDir\src\main\java\com\zynexbd\crmsolution"
if (Test-Path $mainOldDir) {
    if (Test-Path $mainNewDir) { Remove-Item -Recurse -Force $mainNewDir }
    Rename-Item -Path $mainOldDir -NewName "crmsolution"
    Write-Host "   -> Renamed main directory to crmsolution" -ForegroundColor Green
}

$androidTestOldDir = "$appDir\src\androidTest\java\com\zynexbd\livetracking"
$androidTestNewDir = "$appDir\src\androidTest\java\com\zynexbd\crmsolution"
if (Test-Path $androidTestOldDir) {
    if (Test-Path $androidTestNewDir) { Remove-Item -Recurse -Force $androidTestNewDir }
    Rename-Item -Path $androidTestOldDir -NewName "crmsolution"
    Write-Host "   -> Renamed androidTest directory to crmsolution" -ForegroundColor Green
}

Write-Host "`n2. Replacing package references across all source and resource files..." -ForegroundColor Yellow

$files = Get-ChildItem -Path $appDir -Recurse -Include *.kt, *.xml, *.gradle.kts, *.json, *.pro

$count = 0
foreach ($file in $files) {
    # Skip build output directories
    if ($file.FullName -match "\\build\\") { continue }

    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $modified = $false

    if ($content.Contains("com.zynexbd.livetracking")) {
        $content = $content.Replace("com.zynexbd.livetracking", "com.zynexbd.crmsolution")
        $modified = $true
    }

    if ($content.Contains("com.zynexbd.mmfuel")) {
        $content = $content.Replace("com.zynexbd.mmfuel", "com.zynexbd.crmsolution")
        $modified = $true
    }

    if ($modified) {
        [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
        $count++
    }
}

Write-Host "   -> Updated $count files with new package name com.zynexbd.crmsolution." -ForegroundColor Green
