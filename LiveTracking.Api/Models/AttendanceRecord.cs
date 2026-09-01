namespace LiveTracking.Api.Models;

public class AttendanceRecord
{
    public long AttendanceId { get; set; }
    public int UserId { get; set; }
    public string Type { get; set; } = "In"; // "In" or "Out"
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public bool IsWithinGeofence { get; set; } = true;
    public string? SelfieUrl { get; set; }
    public string? Status { get; set; } = "On Time"; // "On Time", "Late (15m)", "Early Out", "Completed"
    public string? ShiftName { get; set; }
    public DateTime RecordedAtUtc { get; set; } = DateTime.UtcNow;

    public User? User { get; set; }
}
