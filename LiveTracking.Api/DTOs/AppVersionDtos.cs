namespace LiveTracking.Api.DTOs;

public class AppVersionCheckResponseDto
{
    public bool HasUpdate { get; set; }
    public bool IsForceUpdate { get; set; }
    public int LatestVersionCode { get; set; }
    public string LatestVersionName { get; set; } = string.Empty;
    public string DownloadUrl { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string ReleaseNotes { get; set; } = string.Empty;
    public int CurrentVersionCode { get; set; }
    public int? CompanyId { get; set; }
    public string? CompanyName { get; set; }
}

public class AppVersionDetailsDto
{
    public int AppVersionId { get; set; }
    public string Platform { get; set; } = "Android";
    public int VersionCode { get; set; }
    public string VersionName { get; set; } = string.Empty;
    public int MinVersionCode { get; set; }
    public bool IsForceUpdate { get; set; }
    public string DownloadUrl { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string ReleaseNotes { get; set; } = string.Empty;
    public bool IsActive { get; set; }
    public int? CompanyId { get; set; }
    public string? CompanyName { get; set; }
    public DateTime CreatedAtUtc { get; set; }
}

public class CreateAppVersionDto
{
    public string Platform { get; set; } = "Android";
    public int VersionCode { get; set; }
    public string VersionName { get; set; } = string.Empty;
    public int MinVersionCode { get; set; } = 1;
    public bool IsForceUpdate { get; set; } = false;
    public string DownloadUrl { get; set; } = string.Empty;
    public string Title { get; set; } = "New Update Available";
    public string ReleaseNotes { get; set; } = string.Empty;
    public int? CompanyId { get; set; }
    public bool IsActive { get; set; } = true;
}
