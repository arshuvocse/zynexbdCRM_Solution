namespace LiveTracking.Api.Models;

public class LeaveType
{
    public int LeaveTypeId { get; set; }
    public string Name { get; set; } = string.Empty;
    public int DefaultDaysPerYear { get; set; }
    public int? CompanyId { get; set; }
    public bool IsActive { get; set; } = true;

    public Company? Company { get; set; }
}

public class LeaveBalance
{
    public int LeaveBalanceId { get; set; }
    public int UserId { get; set; }
    public int LeaveTypeId { get; set; }
    public int Year { get; set; } = DateTime.UtcNow.Year;
    public int TotalDays { get; set; }
    public int UsedDays { get; set; }

    public User? User { get; set; }
    public LeaveType? LeaveType { get; set; }
}

public class LeaveApplication
{
    public int LeaveApplicationId { get; set; }
    public int UserId { get; set; }
    public int LeaveTypeId { get; set; }
    public DateTime StartDate { get; set; }
    public DateTime EndDate { get; set; }
    public int TotalDays { get; set; }
    public string Reason { get; set; } = string.Empty;
    public string Status { get; set; } = "Pending"; // Pending, Approved, Rejected, Cancelled
    public DateTime AppliedAtUtc { get; set; } = DateTime.UtcNow;
    public int? ReviewedBy { get; set; }
    public DateTime? ReviewedAtUtc { get; set; }
    public string? ReviewComment { get; set; }

    public User? User { get; set; }
    public LeaveType? LeaveType { get; set; }
}
