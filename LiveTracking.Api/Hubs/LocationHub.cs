using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;
using LiveTracking.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Hubs;

/// <summary>
/// SignalR Hub for real-time tracking pings and instant push notifications.
/// Admins join their company group "Admins_Company_{companyId}" or global "Admins".
/// Users join "Users", "User_{userId}", and "Users_Company_{companyId}".
/// </summary>
[Authorize]
public class LocationHub : Hub
{
    public const string AdminsGroup = "Admins";
    public const string UsersGroup = "Users";
    public const string AllGroup = "AllUsers";

    public static string UserGroup(int userId) => $"User_{userId}";
    public static string CompanyAdminsGroup(int companyId) => $"Admins_Company_{companyId}";
    public static string CompanyUsersGroup(int companyId) => $"Users_Company_{companyId}";
    public static string CompanyAllGroup(int companyId) => $"All_Company_{companyId}";

    private readonly LiveTrackingDbContext _db;

    public LocationHub(LiveTrackingDbContext db)
    {
        _db = db;
    }

    public override async Task OnConnectedAsync()
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, AllGroup);

        var userIdClaim = Context.User?.FindFirst(ClaimTypes.NameIdentifier)?.Value
            ?? Context.User?.FindFirst("sub")?.Value;

        if (int.TryParse(userIdClaim, out int userId) && userId > 0)
        {
            await Groups.AddToGroupAsync(Context.ConnectionId, UserGroup(userId));

            var user = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.UserId == userId);
            if (user != null)
            {
                if (user.CompanyId.HasValue)
                {
                    await Groups.AddToGroupAsync(Context.ConnectionId, CompanyAllGroup(user.CompanyId.Value));
                }

                if (user.Role == "Admin")
                {
                    if (user.CompanyId.HasValue)
                    {
                        await Groups.AddToGroupAsync(Context.ConnectionId, CompanyAdminsGroup(user.CompanyId.Value));
                    }
                    else
                    {
                        await Groups.AddToGroupAsync(Context.ConnectionId, AdminsGroup);
                    }
                }
                else
                {
                    if (user.CompanyId.HasValue)
                    {
                        await Groups.AddToGroupAsync(Context.ConnectionId, CompanyUsersGroup(user.CompanyId.Value));
                    }
                    await Groups.AddToGroupAsync(Context.ConnectionId, UsersGroup);
                }
            }
        }

        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        await base.OnDisconnectedAsync(exception);
    }
}
