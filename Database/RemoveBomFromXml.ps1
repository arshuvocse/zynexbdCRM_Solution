# ==============================================================================
# REMOVE UTF-8 BOM FROM ALL ANDROID RESOURCE FILES
# ==============================================================================

$resDir = "D:\Shuvo\zynexbd\CRM_Solution\CRM_Apps\app\src\main\res"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$files = Get-ChildItem -Path $resDir -Recurse -Include *.xml

$fixedCount = 0
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        [System.IO.File]::WriteAllText($file.FullName, $text, $utf8NoBom)
        $fixedCount++
    }
}

Write-Host "Removed UTF-8 BOM from $fixedCount XML resource files." -ForegroundColor Green
