namespace LiveTracking.Api.Models.CRM;

public class CrmLeadAssignment
{
    public int AssignmentId { get; set; }
    public int CompanyId { get; set; }
    public int LeadId { get; set; }
    public int? PreviousUserId { get; set; }
    public int NewUserId { get; set; }
    public int AssignedByUserId { get; set; }
    public DateTime AssignedDateUtc { get; set; } = DateTime.UtcNow;
    public string? Remarks { get; set; }
    public int? OfficeLocationId { get; set; }

    public Company? Company { get; set; }
    public OfficeLocation? OfficeLocation { get; set; }
    public CrmLead? Lead { get; set; }
    public User? PreviousUser { get; set; }
    public User? NewUser { get; set; }
    public User? AssignedByUser { get; set; }
}
