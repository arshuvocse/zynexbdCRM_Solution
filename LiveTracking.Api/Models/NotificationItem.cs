using System.ComponentModel.DataAnnotations;

namespace LiveTracking.Api.Models;

public class NotificationItem
{
    [Key]
    public int NotificationId { get; set; }

    public int? UserId { get; set; } // null or 0 for broadcast / role-based
    public User? User { get; set; }

    public int? CompanyId { get; set; }
    public Company? Company { get; set; }

    [MaxLength(20)]
    public string TargetRole { get; set; } = "All"; // "Admin", "User", "All"

    [Required]
    [MaxLength(200)]
    public string Title { get; set; } = string.Empty;

    [Required]
    [MaxLength(1000)]
    public string Message { get; set; } = string.Empty;

    [MaxLength(50)]
    public string Type { get; set; } = "General"; // "Attendance", "Leave", "Visit", "Announcement", "General"

    [MaxLength(100)]
    public string? ReferenceId { get; set; }

    public bool IsRead { get; set; } = false;

    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
}
