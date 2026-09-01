namespace LiveTracking.Api.Models;

public class CustomerVisit
{
    public long VisitId { get; set; }
    public int CustomerId { get; set; }
    public Customer? Customer { get; set; }
    public int UserId { get; set; }
    public User? User { get; set; }
    public DateTime VisitDate { get; set; } = DateTime.UtcNow;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public string? Remarks { get; set; }
    public string VisitStatus { get; set; } = "Completed";
    public DateTime? NextFollowUpDate { get; set; }
    public string? ShopPhotoPath { get; set; }
    public bool IsFollowUpCompleted { get; set; } = false;
}
