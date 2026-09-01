namespace LiveTracking.Api.Models;

public class AdminOfficeLocation
{
    public int Id { get; set; }
    public int AdminUserId { get; set; }
    public int OfficeLocationId { get; set; }
    public DateTime AssignedAtUtc { get; set; } = DateTime.UtcNow;

    public User? AdminUser { get; set; }
    public OfficeLocation? OfficeLocation { get; set; }
}
