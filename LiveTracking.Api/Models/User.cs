namespace LiveTracking.Api.Models;

public class User
{
    public int UserId { get; set; }
    public string Username { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;
    public string Role { get; set; } = "User"; // "Admin" or "User"
    public bool IsActive { get; set; } = true;
    public string? PhoneNumber { get; set; }
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
    public DateTime? UpdatedAtUtc { get; set; }
    public int? OfficeLocationId { get; set; }
    public int? CreatedByAdminId { get; set; }
    public int? ShiftId { get; set; }
    public int? CompanyId { get; set; }
    public int? MaxUserLimit { get; set; }
    public string? BoundDeviceId { get; set; }
    public string? DeviceModel { get; set; }
    public DateTime? PaymentDueDate { get; set; }

    public Company? Company { get; set; }
    public Shift? Shift { get; set; }
    public OfficeLocation? OfficeLocation { get; set; }
    public ICollection<DriverLocation> Locations { get; set; } = new List<DriverLocation>();
    public ICollection<AdminOfficeLocation> AdminOfficeLocations { get; set; } = new List<AdminOfficeLocation>();
}
