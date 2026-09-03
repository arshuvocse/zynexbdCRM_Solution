namespace LiveTracking.Api.DTOs;

public record LoginRequest(string Username, string Password, string? DeviceId = null, string? DeviceModel = null);

public record AuthorizedOfficeDto(int OfficeLocationId, string Name);

public record LoginResponse(
    string Token,
    string ExpiresAt,
    int UserId,
    string Name,
    string Username,
    string Role,
    int? CompanyId = null,
    string? CompanyName = null,
    string? CompanyLogoUrl = null,
    int? OfficeLocationId = null,
    string? OfficeLocationName = null,
    List<AuthorizedOfficeDto>? AuthorizedOfficeLocations = null
);

public record ApiResponse<T>(
    bool Success,
    string Message,
    T? Data = default,
    List<string>? Errors = null
);
