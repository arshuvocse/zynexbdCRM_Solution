namespace LiveTracking.Api.Models.CRM;

public class CrmLeadSource
{
    public int LeadSourceId { get; set; }
    public int CompanyId { get; set; }
    public string Name { get; set; } = string.Empty;
    public bool IsSystem { get; set; } = false;
    public bool IsActive { get; set; } = true;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public Company? Company { get; set; }
}
