Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\NASA\.gemini\antigravity-ide\brain\0bd7c2f8-0fdf-46bb-9ced-077452837335\crm_app_logo_1788191581846.jpg"
$resDir = "D:\Shuvo\zynexbd\CRM_Solution\CRM_Apps\app\src\main\res"

if (-not (Test-Path $srcPath)) {
    Write-Error "Source image not found at $srcPath"
    exit 1
}

$srcBmp = [System.Drawing.Bitmap]::FromFile($srcPath)

function Resize-Image($src, $w, $h) {
    $dest = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dest)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($src, 0, 0, $w, $h)
    $g.Dispose()
    return $dest
}

function Create-Round-Image($src, $size) {
    $resized = Resize-Image $src $size $size
    $dest = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dest)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $size, $size)
    $g.SetClip($path)
    $g.DrawImage($resized, 0, 0, $size, $size)
    $g.Dispose()
    $resized.Dispose()
    return $dest
}

# 1. Generate app_logo.png (512x512)
$appLogo = Resize-Image $srcBmp 512 512
$appLogo.Save("$resDir\drawable\app_logo.png", [System.Drawing.Imaging.ImageFormat]::Png)
$appLogo.Dispose()
Write-Host "Generated drawable\app_logo.png"

# 2. Generate ic_launcher_foreground.png (432x432)
$fg = Resize-Image $srcBmp 432 432
$fg.Save("$resDir\drawable\ic_launcher_foreground.png", [System.Drawing.Imaging.ImageFormat]::Png)
$fg.Dispose()
Write-Host "Generated drawable\ic_launcher_foreground.png"

# 3. Densities for mipmap
$densities = @(
    @{ Dir = "mipmap-mdpi"; Size = 48 },
    @{ Dir = "mipmap-hdpi"; Size = 72 },
    @{ Dir = "mipmap-xhdpi"; Size = 96 },
    @{ Dir = "mipmap-xxhdpi"; Size = 144 },
    @{ Dir = "mipmap-xxxhdpi"; Size = 192 }
)

foreach ($d in $densities) {
    $dirPath = "$resDir\$($d.Dir)"
    if (-not (Test-Path $dirPath)) {
        New-Item -ItemType Directory -Path $dirPath -Force | Out-Null
    }
    
    # Square / standard icon
    $square = Resize-Image $srcBmp $d.Size $d.Size
    $square.Save("$dirPath\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $square.Dispose()
    
    # Round icon
    $round = Create-Round-Image $srcBmp $d.Size
    $round.Save("$dirPath\ic_launcher_round.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $round.Dispose()
    
    Write-Host "Generated $($d.Dir): $($d.Size)x$($d.Size) standard & round"
}

$srcBmp.Dispose()
Write-Host "All Android App Icons generated successfully!"
