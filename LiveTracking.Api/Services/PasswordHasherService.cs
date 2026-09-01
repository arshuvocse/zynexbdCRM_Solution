using LiveTracking.Api.Models;
using Microsoft.AspNetCore.Identity;

namespace LiveTracking.Api.Services;

public interface IPasswordHasherService
{
    string Hash(User user, string password);
    bool Verify(User user, string hash, string password);
}

public class PasswordHasherService : IPasswordHasherService
{
    private readonly PasswordHasher<User> _hasher = new();

    public string Hash(User user, string password) => _hasher.HashPassword(user, password);

    public bool Verify(User user, string hash, string password)
    {
        if (string.IsNullOrEmpty(hash) || string.IsNullOrEmpty(password)) return false;

        // Legacy accounts seeded with a plaintext PasswordHash column (pre-dates proper hashing).
        // AuthController rehashes these transparently on successful login - see MigratePlaintextHashIfNeeded.
        if (hash == password) return true;

        if (hash.StartsWith("$2a$") || hash.StartsWith("$2b$") || hash.StartsWith("$2y$"))
        {
            try
            {
                return BCrypt.Net.BCrypt.Verify(password, hash);
            }
            catch
            {
                // Fall through if BCrypt verification fails format check
            }
        }

        try
        {
            return _hasher.VerifyHashedPassword(user, hash, password) != PasswordVerificationResult.Failed;
        }
        catch
        {
            return false;
        }
    }
}
