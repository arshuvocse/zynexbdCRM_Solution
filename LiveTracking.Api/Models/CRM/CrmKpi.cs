namespace LiveTracking.Api.Models.CRM;

public class CrmKpi
{
    public int KpiId { get; set; }
    public int CompanyId { get; set; }
    public int? UserId { get; set; } // Null for company default, non-null for specific employee
    public string PeriodType { get; set; } = "Daily"; // 'Daily', 'Weekly', 'Monthly'
    public int FollowUpTarget { get; set; } = 0;
    public int InterestedTarget { get; set; } = 0;
    public int ClosedTarget { get; set; } = 0;
    public DateTime EffectiveStartDate { get; set; } = DateTime.UtcNow;
    public bool IsActive { get; set; } = true;
    public int CreatedByUserId { get; set; }
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
    public DateTime? UpdatedAtUtc { get; set; }
    public int? OfficeLocationId { get; set; }

    public Company? Company { get; set; }
    public OfficeLocation? OfficeLocation { get; set; }
    public User? User { get; set; }
    public User? CreatedByUser { get; set; }
}
