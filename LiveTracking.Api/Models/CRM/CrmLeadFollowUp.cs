namespace LiveTracking.Api.Models.CRM;

public class CrmLeadFollowUp
{
    public int FollowUpId { get; set; }
    public int CompanyId { get; set; }
    public int LeadId { get; set; }
    public DateTime FollowUpDateUtc { get; set; } = DateTime.UtcNow;
    public DateTime? NextFollowUpDate { get; set; }
    public string Status { get; set; } = "Follow Up";
    public string Remarks { get; set; } = string.Empty;
    public int CreatedByUserId { get; set; }
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
    public int? OfficeLocationId { get; set; }

    public Company? Company { get; set; }
    public OfficeLocation? OfficeLocation { get; set; }
    public CrmLead? Lead { get; set; }
    public User? CreatedByUser { get; set; }
}
