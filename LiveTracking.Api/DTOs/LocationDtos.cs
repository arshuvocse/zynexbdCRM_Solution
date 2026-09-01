using System.Text.Json.Serialization;

namespace LiveTracking.Api.DTOs;

public class LocationPingRequest
{
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double? Accuracy { get; set; }
    public double? Speed { get; set; }
    public double? Bearing { get; set; }

    [JsonPropertyName("recordedAt")]
    public string? RecordedAt { get; set; }

    [JsonPropertyName("recordedAtUtc")]
    public DateTime? RecordedAtUtc { get; set; }

    [JsonPropertyName("deviceBattery")]
    public int? DeviceBattery { get; set; }

    [JsonPropertyName("batteryLevel")]
    public double? BatteryLevel { get; set; }

    [JsonPropertyName("networkType")]
    public string? NetworkType { get; set; }

    [JsonPropertyName("locationAddress")]
    public string? LocationAddress { get; set; }
}

public record LocationResponse(
    int UserId,
    string Username,
    string FullName,
    bool IsActive,
    double? Latitude,
    double? Longitude,
    double? Accuracy,
    double? Speed,
    double? Bearing,
    DateTime? RecordedAtUtc,
    string? LocationAddress = null);
