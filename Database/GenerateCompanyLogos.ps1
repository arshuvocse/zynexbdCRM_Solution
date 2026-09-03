$code = @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public class LogoGen
{
    public static void CreateLogo(string path, string text, Color c1, Color c2)
    {
        using (var bmp = new Bitmap(256, 256))
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            using (var brush = new LinearGradientBrush(new Point(0, 0), new Point(256, 256), c1, c2))
            {
                g.FillEllipse(brush, 8, 8, 240, 240);
            }
            using (var font = new Font("Arial", 64, FontStyle.Bold, GraphicsUnit.Pixel))
            using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
            {
                g.DrawString(text, font, Brushes.White, new RectangleF(0, 0, 256, 256), sf);
            }
            bmp.Save(path, ImageFormat.Png);
        }
    }
}
"@

Add-Type -TypeDefinition $code -ReferencedAssemblies "System.Drawing"
$dir = "LiveTracking.Api\wwwroot\uploads\companies"
if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force }

[LogoGen]::CreateLogo("$dir\company1_logo.png", "DO", [System.Drawing.Color]::FromArgb(255, 37, 99, 235), [System.Drawing.Color]::FromArgb(255, 79, 70, 229))
[LogoGen]::CreateLogo("$dir\company2_logo.png", "BS", [System.Drawing.Color]::FromArgb(255, 16, 185, 129), [System.Drawing.Color]::FromArgb(255, 13, 148, 136))
Write-Host "Created company1_logo.png and company2_logo.png successfully."
