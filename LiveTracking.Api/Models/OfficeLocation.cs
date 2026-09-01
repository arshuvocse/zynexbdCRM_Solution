namespace LiveTracking.Api.Models;

public class OfficeLocation
{
    public int OfficeLocationId { get; set; }
    public string Name { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double RadiusMeters { get; set; } = 100.0;
    public string? Address { get; set; }
    public bool IsActive { get; set; } = true;
    public int? CompanyId { get; set; }
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public Company? Company { get; set; }
    public ICollection<AdminOfficeLocation> AdminOfficeLocations { get; set; } = new List<AdminOfficeLocation>();
}
