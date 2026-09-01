namespace LiveTracking.Api.DTOs;

public class NotificationDto
{
    public int NotificationId { get; set; }
    public int? UserId { get; set; }
    public int? CompanyId { get; set; }
    public string TargetRole { get; set; } = "All";
    public string Title { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public string Type { get; set; } = "General";
    public string? ReferenceId { get; set; }
    public bool IsRead { get; set; }
    public DateTime CreatedAtUtc { get; set; }
}

public class SendNotificationRequestDto
{
    public int? UserId { get; set; }
    public int? CompanyId { get; set; }
    public string TargetRole { get; set; } = "All"; // "Admin", "User", "All"
    public string Title { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public string Type { get; set; } = "General";
    public string? ReferenceId { get; set; }
}

public class UnreadCountResponseDto
{
    public int UnreadCount { get; set; }
}
