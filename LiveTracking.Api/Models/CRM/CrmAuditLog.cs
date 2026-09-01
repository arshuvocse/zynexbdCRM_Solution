namespace LiveTracking.Api.Models.CRM;

public class CrmAuditLog
{
    public int AuditLogId { get; set; }
    public int CompanyId { get; set; }
    public int UserId { get; set; }
    public string Action { get; set; } = string.Empty; // LeadCreated, LeadAssigned, LeadReassigned, StatusChanged, FollowUpAdded, RemarkAdded, KpiCreated, KpiUpdated
    public string EntityType { get; set; } = string.Empty; // Lead, Kpi
    public int EntityId { get; set; }
    public string? OldValue { get; set; }
    public string? NewValue { get; set; }
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;

    public Company? Company { get; set; }
    public User? User { get; set; }
}
