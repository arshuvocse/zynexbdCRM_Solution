namespace LiveTracking.Api.Models.CRM;

public class CrmLead
{
    public int LeadId { get; set; }
    public int CompanyId { get; set; }
    public string LeadName { get; set; } = string.Empty;
    public string? ContactPerson { get; set; }
    public string? Phone { get; set; }
    public string? Email { get; set; }
    public string? Address { get; set; }
    public int? ProductServiceId { get; set; }
    public int? LeadSourceId { get; set; }
    public string LeadSourceType { get; set; } = "Self"; // 'Self' or 'Assigned'
    public string LeadStatus { get; set; } = "New Lead"; // 'New Lead', 'Follow Up', 'Interested', 'Not Interested', 'Closed'
    public int CreatedByUserId { get; set; }
    public int? AssignedUserId { get; set; }
    public DateTime? NextFollowUpDate { get; set; }
    public DateTime? LastFollowUpDate { get; set; }
    public decimal? EstimatedValue { get; set; }
    public string? Remarks { get; set; }
    public bool IsActive { get; set; } = true;
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
    public DateTime? UpdatedAtUtc { get; set; }
    public int? OfficeLocationId { get; set; }

    public Company? Company { get; set; }
    public OfficeLocation? OfficeLocation { get; set; }
    public CrmProductService? ProductService { get; set; }
    public CrmLeadSource? LeadSource { get; set; }
    public User? CreatedByUser { get; set; }
    public User? AssignedUser { get; set; }

    public ICollection<CrmLeadAssignment> Assignments { get; set; } = new List<CrmLeadAssignment>();
    public ICollection<CrmLeadFollowUp> FollowUps { get; set; } = new List<CrmLeadFollowUp>();
    public ICollection<CrmLeadRemark> RemarksHistory { get; set; } = new List<CrmLeadRemark>();
    public ICollection<CrmLeadStatusHistory> StatusHistory { get; set; } = new List<CrmLeadStatusHistory>();
}
