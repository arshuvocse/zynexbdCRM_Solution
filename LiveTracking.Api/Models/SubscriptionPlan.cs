using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace LiveTracking.Api.Models;

[Table("SubscriptionPlans")]
public class SubscriptionPlan
{
    [Key]
    public int PlanId { get; set; }

    [Required]
    [MaxLength(50)]
    public string PlanCode { get; set; } = string.Empty;

    [Required]
    [MaxLength(50)]
    public string TierName { get; set; } = string.Empty;

    [Required]
    [MaxLength(100)]
    public string Title { get; set; } = string.Empty;

    [Required]
    [MaxLength(100)]
    public string TitleBn { get; set; } = string.Empty;

    public int DurationMonths { get; set; }

    [Column(TypeName = "decimal(18,2)")]
    public decimal Price { get; set; }

    [Column(TypeName = "decimal(18,2)")]
    public decimal OriginalPrice { get; set; }

    public int DiscountPercent { get; set; }

    [MaxLength(100)]
    public string? DiscountText { get; set; }

    [MaxLength(50)]
    public string? BadgeText { get; set; }

    [MaxLength(50)]
    public string? BadgeTextBn { get; set; }

    public string FeaturesJson { get; set; } = "[]";

    public bool IsActive { get; set; } = true;

    public int DisplayOrder { get; set; }

    public DateTime CreatedDate { get; set; } = DateTime.UtcNow;
}
