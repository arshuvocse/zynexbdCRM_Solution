namespace LiveTracking.Api.DTOs;

public class LeaveTypeDto
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public int DefaultDaysPerYear { get; set; }
    public bool IsActive { get; set; }

    public LeaveTypeDto() { }
    public LeaveTypeDto(int id, string name, int defaultDaysPerYear, bool isActive)
    {
        Id = id;
        Name = name;
        DefaultDaysPerYear = defaultDaysPerYear;
        IsActive = isActive;
    }
}

public class LeaveBalanceDto
{
    public int LeaveTypeId { get; set; }
    public string LeaveTypeName { get; set; } = string.Empty;
    public int TotalDays { get; set; }
    public int UsedDays { get; set; }
    public int RemainingDays { get; set; }

    public LeaveBalanceDto() { }
    public LeaveBalanceDto(int leaveTypeId, string leaveTypeName, int totalDays, int usedDays, int remainingDays)
    {
        LeaveTypeId = leaveTypeId;
        LeaveTypeName = leaveTypeName;
        TotalDays = totalDays;
        UsedDays = usedDays;
        RemainingDays = remainingDays;
    }
}

public class ApplyLeaveRequestDto
{
    public int LeaveTypeId { get; set; }
    public string StartDate { get; set; } = string.Empty;
    public string EndDate { get; set; } = string.Empty;
    public string Reason { get; set; } = string.Empty;
}

public class LeaveReviewRequestDto
{
    public string? Comment { get; set; }
}

public class LeaveApplicationResponseDto
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public string? UserName { get; set; }
    public int LeaveTypeId { get; set; }
    public string? LeaveTypeName { get; set; }
    public string? StartDate { get; set; }
    public string? EndDate { get; set; }
    public int TotalDays { get; set; }
    public string? Reason { get; set; }
    public string? Status { get; set; }
    public string? AppliedAt { get; set; }
    public string? ReviewedAt { get; set; }
    public string? ReviewComment { get; set; }

    public LeaveApplicationResponseDto() { }
    public LeaveApplicationResponseDto(int id, int userId, string? userName, int leaveTypeId, string? leaveTypeName, string? startDate, string? endDate, int totalDays, string? reason, string? status, string? appliedAt, string? reviewedAt, string? reviewComment)
    {
        Id = id;
        UserId = userId;
        UserName = userName;
        LeaveTypeId = leaveTypeId;
        LeaveTypeName = leaveTypeName;
        StartDate = startDate;
        EndDate = endDate;
        TotalDays = totalDays;
        Reason = reason;
        Status = status;
        AppliedAt = appliedAt;
        ReviewedAt = reviewedAt;
        ReviewComment = reviewComment;
    }
}

public record PendingLeaveCountDto(int Count);
