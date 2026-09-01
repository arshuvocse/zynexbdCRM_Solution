namespace LiveTracking.Api.DTOs;

public record CompanyDto(
    int CompanyId,
    string CompanyName,
    string CompanyCode,
    string? ContactPerson,
    string? ContactPhone,
    string? ContactEmail,
    int MaxUserLimit,
    string? PaymentDueDate,
    bool IsActive,
    int TotalOffices,
    int TotalAdmins,
    int ActiveOfficersCount,
    string SubscriptionStatus
);

public record CreateCompanyRequest(
    string CompanyName,
    string CompanyCode,
    string? ContactPerson,
    string? ContactPhone,
    string? ContactEmail,
    int? MaxUserLimit,
    string? PaymentDueDate
);

public record UpdateCompanyRequest(
    string? CompanyName,
    string? ContactPerson,
    string? ContactPhone,
    string? ContactEmail,
    int? MaxUserLimit,
    string? PaymentDueDate,
    bool? IsActive
);

public record CompanyStatsDto(
    int CompanyId,
    string CompanyName,
    string CompanyCode,
    int TotalOffices,
    int TotalAdmins,
    int ActiveOfficersCount,
    int MaxUserLimit,
    bool IsQuotaFull,
    string? PaymentDueDate,
    string SubscriptionStatus
);
