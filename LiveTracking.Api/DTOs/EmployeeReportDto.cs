namespace LiveTracking.Api.DTOs;

public class MonthlyPerformanceReportResponse
{
    public int Year { get; set; }
    public int Month { get; set; }
    public string MonthName { get; set; } = string.Empty;
    public int TotalVisits { get; set; }
    public int TotalFollowUps { get; set; }
    public int CompletedFollowUps { get; set; }
    public int PendingFollowUps { get; set; }
    public int TotalCustomersAdded { get; set; }
    public List<EmployeePerformanceItemDto> Employees { get; set; } = new();
}

public class EmployeePerformanceItemDto
{
    public int UserId { get; set; }
    public string FullName { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public string Role { get; set; } = string.Empty;
    public bool IsActive { get; set; } = true;
    public int TotalCustomersAdded { get; set; }
    public int TotalVisits { get; set; }
    public int TotalFollowUps { get; set; }
    public int CompletedFollowUps { get; set; }
    public int PendingFollowUps { get; set; }
    public List<ReportCustomerItemDto> Customers { get; set; } = new();
    public List<ReportVisitItemDto> Visits { get; set; } = new();
    public List<ReportFollowUpItemDto> FollowUps { get; set; } = new();
}

public class ReportCustomerItemDto
{
    public int CustomerId { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Mobile { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public DateTime CreatedDate { get; set; }
    public string? Remarks { get; set; }
}

public class ReportVisitItemDto
{
    public long VisitId { get; set; }
    public int CustomerId { get; set; }
    public string CustomerName { get; set; } = string.Empty;
    public string CustomerMobile { get; set; } = string.Empty;
    public string CustomerAddress { get; set; } = string.Empty;
    public DateTime VisitDate { get; set; }
    public string? Remarks { get; set; }
    public string VisitStatus { get; set; } = "Completed";
    public string? ShopPhotoPath { get; set; }
}

public class ReportFollowUpItemDto
{
    public long VisitId { get; set; }
    public int CustomerId { get; set; }
    public string CustomerName { get; set; } = string.Empty;
    public string CustomerMobile { get; set; } = string.Empty;
    public DateTime? FollowUpDate { get; set; }
    public bool IsCompleted { get; set; }
    public string? Remarks { get; set; }
}
