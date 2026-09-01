namespace LiveTracking.Api.Services;

/// <summary>
/// Resolved office-location authorization for the current CRM caller. IsUnrestricted=true
/// only for an Admin with no office assignments configured (company-wide, preserves existing
/// behavior). A Manager with no resolvable office is represented by an empty, non-unrestricted
/// scope (Allows() returns false for everything - fail closed, never fail open).
/// </summary>
public sealed record CrmOfficeScope(List<int> OfficeIds, bool IsUnrestricted)
{
    public static readonly CrmOfficeScope Unrestricted = new(new List<int>(), true);
    public static readonly CrmOfficeScope None = new(new List<int>(), false);

    public bool Allows(int? officeLocationId) =>
        IsUnrestricted || (officeLocationId.HasValue && OfficeIds.Contains(officeLocationId.Value));
}
