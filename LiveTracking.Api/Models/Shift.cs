namespace LiveTracking.Api.Models;

public class Shift
{
    public int ShiftId { get; set; }
    public string ShiftName { get; set; } = string.Empty;
    public string StartTime { get; set; } = "09:00:00"; // Format "HH:mm:ss" or "HH:mm"
    public string EndTime { get; set; } = "18:00:00";   // Format "HH:mm:ss" or "HH:mm"
    public int GracePeriodMinutes { get; set; } = 15;
    public bool IsDefault { get; set; } = false;
    public bool IsActive { get; set; } = true;
    public int? CreatedByAdminId { get; set; }
    public int? CompanyId { get; set; }
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public Company? Company { get; set; }
}
