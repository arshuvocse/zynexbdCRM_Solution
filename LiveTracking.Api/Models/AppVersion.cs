using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace LiveTracking.Api.Models;

public class AppVersion
{
    [Key]
    public int AppVersionId { get; set; }

    [Required]
    [MaxLength(30)]
    public string Platform { get; set; } = "Android"; // "Android", "iOS"

    public int VersionCode { get; set; }

    [Required]
    [MaxLength(50)]
    public string VersionName { get; set; } = string.Empty;

    public int MinVersionCode { get; set; } = 1;

    public bool IsForceUpdate { get; set; } = false;

    [Required]
    [MaxLength(1000)]
    public string DownloadUrl { get; set; } = string.Empty;

    [MaxLength(200)]
    public string Title { get; set; } = "New Update Available";

    public string ReleaseNotes { get; set; } = string.Empty;

    public bool IsActive { get; set; } = true;

    public int? CompanyId { get; set; }

    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public Company? Company { get; set; }
}
