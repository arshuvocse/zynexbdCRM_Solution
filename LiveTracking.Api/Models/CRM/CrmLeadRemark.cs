namespace LiveTracking.Api.Models.CRM;

public class CrmLeadRemark
{
    public int RemarkId { get; set; }
    public int CompanyId { get; set; }
    public int LeadId { get; set; }
    public int UserId { get; set; }
    public string Remark { get; set; } = string.Empty;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public Company? Company { get; set; }
    public CrmLead? Lead { get; set; }
    public User? User { get; set; }
}
