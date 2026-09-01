namespace LiveTracking.Api.Models;

public class DriverLocation
{
    public long LocationId { get; set; }
    public int UserId { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double? Accuracy { get; set; }
    public double? Speed { get; set; }
    public double? Bearing { get; set; }
    public DateTime RecordedAtUtc { get; set; }
    public DateTime ReceivedAtUtc { get; set; } = DateTime.UtcNow;
    public string? LocationAddress { get; set; }

    public User? User { get; set; }
}
