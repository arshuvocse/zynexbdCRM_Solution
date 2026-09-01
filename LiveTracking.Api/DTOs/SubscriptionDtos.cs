namespace LiveTracking.Api.DTOs;

public record SubscriptionStatusDto(
    int AdminId,
    string AdminName,
    string AdminUsername,
    string? AdminPhone,
    DateTime? PaymentDueDate,
    int DaysRemaining,
    bool IsExpired,
    bool IsWarningPeriod,
    string StatusText
);

public record UpdatePaymentDueDateRequest(
    int AdminId,
    DateTime NewDueDate
);
