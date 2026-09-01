namespace LiveTracking.Api.DTOs;

public record ExecutiveSummaryDto(
    int TotalUsers,
    int ActiveUsers,
    int OnlineTrackingUsers,
    int TodayPunchInCount,
    int TodayAbsentCount,
    int TodayLateCount,
    int PendingLeaveRequestsCount,
    int PendingCustomerFollowUpsCount,
    int GpsDisabledUsersCount,
    List<AttentionItemDto> AttentionItems,
    SystemHealthDto SystemHealth
);

public record AttentionItemDto(
    string Id,
    string Type, // "GpsDisabled", "NoLocation", "OfflineInShift", "LateAttendance", "MissedFollowUp", "PendingLeave"
    string Title,
    string Description,
    int? UserId,
    string? UserName,
    string Severity, // "High", "Medium", "Low"
    string ActionType, // "Track", "Call", "Approve", "View"
    string Timestamp
);

public record SystemHealthDto(
    string DbStatus,
    string SignalRStatus,
    int ActiveSignalRConnections,
    string LastCheckedAt
);

public record BulkLeaveApprovalDto(
    List<int> ApplicationIds,
    string? Comment
);

public record BulkLeaveApprovalResultDto(
    int ApprovedCount,
    int FailedCount,
    List<int> ApprovedIds
);
