namespace LiveTracking.Api.Models;

public class Company
{
    public int CompanyId { get; set; }
    public string CompanyName { get; set; } = string.Empty;
    public string CompanyCode { get; set; } = string.Empty;
    public string? ContactPerson { get; set; }
    public string? ContactPhone { get; set; }
    public string? ContactEmail { get; set; }
    public int MaxUserLimit { get; set; } = 10;
    public DateTime? PaymentDueDate { get; set; }
    public bool IsActive { get; set; } = true;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
    public DateTime? UpdatedAtUtc { get; set; }

    public ICollection<User> Users { get; set; } = new List<User>();
    public ICollection<OfficeLocation> OfficeLocations { get; set; } = new List<OfficeLocation>();
    public ICollection<Shift> Shifts { get; set; } = new List<Shift>();
    public ICollection<LeaveType> LeaveTypes { get; set; } = new List<LeaveType>();
    public ICollection<AppVersion> AppVersions { get; set; } = new List<AppVersion>();
}
