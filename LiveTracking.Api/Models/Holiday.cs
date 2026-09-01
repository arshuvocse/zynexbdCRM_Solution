namespace LiveTracking.Api.Models;

public class Holiday
{
    public int HolidayId { get; set; }
    public string Name { get; set; } = string.Empty;
    public DateTime Date { get; set; }
    public int Year { get; set; }
    public bool IsRecurring { get; set; } = false;
    public bool IsActive { get; set; } = true;
    public string? Description { get; set; }
}
