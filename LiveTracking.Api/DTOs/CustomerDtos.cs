namespace LiveTracking.Api.DTOs;

public class CreateCustomerRequest
{
    public string Name { get; set; } = string.Empty;
    public string Mobile { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public string? Remarks { get; set; }
}

public class CustomerResponse
{
    public int CustomerId { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Mobile { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public string? Remarks { get; set; }
    public DateTime CreatedDate { get; set; }
    public bool IsActive { get; set; }
    public int? CreatedByUserId { get; set; }
    public string? CreatedByUserName { get; set; }
    public DateTime? LastVisitDate { get; set; }
    public DateTime? NextFollowUpDate { get; set; }
}

public class RecordVisitRequest
{
    public int CustomerId { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public string? Remarks { get; set; }
    public string VisitStatus { get; set; } = "Completed";
    public DateTime? NextFollowUpDate { get; set; }
    public string? ShopPhotoBase64 { get; set; }
}

public class CustomerVisitResponse
{
    public long VisitId { get; set; }
    public int CustomerId { get; set; }
    public string CustomerName { get; set; } = string.Empty;
    public string Mobile { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public int UserId { get; set; }
    public string UserName { get; set; } = string.Empty;
    public DateTime VisitDate { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public string? Remarks { get; set; }
    public string VisitStatus { get; set; } = string.Empty;
    public DateTime? NextFollowUpDate { get; set; }
    public string? ShopPhotoPath { get; set; }
    public bool IsFollowUpCompleted { get; set; }
}

public class FollowUpResponse
{
    public long VisitId { get; set; }
    public int CustomerId { get; set; }
    public string CustomerName { get; set; } = string.Empty;
    public string Mobile { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public DateTime? FollowUpDate { get; set; }
    public string Category { get; set; } = "Today"; // Today, Tomorrow, Upcoming, Overdue
    public bool IsCompleted { get; set; }
    public string? Remarks { get; set; }
}

public class FieldUserDashboardStats
{
    public string AttendanceStatus { get; set; } = "Not Punched In"; // "Punched In" / "Not Punched In" / "Punched Out"
    public string? PunchInTime { get; set; }
    public string? PunchOutTime { get; set; }
    public string TodayWorkingTime { get; set; } = "0h 0m";
    public int TodayVisitsCount { get; set; }
    public int PendingFollowUpsCount { get; set; }
    public bool GpsTrackingActive { get; set; } = true;
}
