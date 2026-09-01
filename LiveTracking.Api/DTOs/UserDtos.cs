namespace LiveTracking.Api.DTOs;

public record UserResponse(
    int UserId,
    string Username,
    string FullName,
    string Role,
    bool IsActive,
    string? PhoneNumber,
    int? OfficeLocationId,
    string? OfficeLocationName,
    int? CreatedByAdminId = null,
    int? MaxUserLimit = null,
    string? BoundDeviceId = null,
    string? DeviceModel = null,
    List<int>? AssignedOfficeLocationIds = null,
    List<string>? AssignedOfficeLocationNames = null
);

public record CreateUserRequest(
    string Username,
    string Password,
    string FullName,
    string Role,
    string? PhoneNumber,
    int? OfficeLocationId = null,
    int? MaxUserLimit = null,
    List<int>? AssignedOfficeLocationIds = null
);

public record UpdateUserRequest(
    string FullName,
    string? PhoneNumber,
    string? Role = null,
    bool? IsActive = null,
    int? OfficeLocationId = null,
    int? MaxUserLimit = null,
    List<int>? AssignedOfficeLocationIds = null
);

public record ResetPasswordRequest(string NewPassword);

public record SetActiveRequest(bool IsActive);

public record AdminUserQuotaDto(int MaxUserLimit, int UsedUserCount, int RemainingUserCount, bool IsLimitReached);

public record OfficeLocationDto(
    int OfficeLocationId,
    string Name,
    double Latitude,
    double Longitude,
    double RadiusMeters,
    string? Address,
    bool IsActive,
    DateTime CreatedAtUtc
);

public record CreateOfficeLocationRequest(
    string Name,
    double Latitude,
    double Longitude,
    double RadiusMeters = 200.0,
    string? Address = null
);

public record UpdateOfficeLocationRequest(
    string Name,
    double Latitude,
    double Longitude,
    double RadiusMeters = 200.0,
    string? Address = null,
    bool IsActive = true
);
