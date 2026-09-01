namespace LiveTracking.Api.Models.CRM;

public class CrmLeadStatusHistory
{
    public int StatusHistoryId { get; set; }
    public int CompanyId { get; set; }
    public int LeadId { get; set; }
    public string PreviousStatus { get; set; } = string.Empty;
    public string NewStatus { get; set; } = string.Empty;
    public int ChangedByUserId { get; set; }
    public DateTime ChangedDateUtc { get; set; } = DateTime.UtcNow;
    public string? Remarks { get; set; }

    public Company? Company { get; set; }
    public CrmLead? Lead { get; set; }
    public User? ChangedByUser { get; set; }
}
