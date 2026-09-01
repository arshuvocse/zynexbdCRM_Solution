using Microsoft.AspNetCore.Http;

namespace LiveTracking.Api.DTOs;

public class AttendancePunchRequest
{
    public IFormFile? Selfie { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
}

public record AttendanceResponseDto(
    long Id,
    int UserId,
    string? UserName,
    string? Type,
    string? Timestamp,
    double Latitude,
    double Longitude,
    bool IsWithinGeofence,
    string? SelfieUrl,
    string? Status = "On Time",
    string? ShiftName = "General Shift"
);

public record EmployeeMonthlyAttendanceSummaryDto(
    int UserId,
    string FullName,
    string Username,
    string Role,
    string ShiftName,
    int Year,
    int Month,
    int TotalWorkingDays,
    int PresentDays,
    int OnTimeDays,
    int LateDays,
    int EarlyOutDays,
    int ApprovedLeaveDays,
    int AbsentDays,
    double AttendancePercentage,
    string TotalPresenceTime = "00:00"
);

public class TodayAttendanceStatusDto
{
    public bool HasPunchedIn { get; set; }
    public string? PunchInTime { get; set; }
    public string? PunchInStatus { get; set; }
    public string? PunchInSelfieUrl { get; set; }

    public bool HasPunchedOut { get; set; }
    public string? PunchOutTime { get; set; }
    public string? PunchOutStatus { get; set; }
    public string? PunchOutSelfieUrl { get; set; }

    public string? ShiftName { get; set; }
}
