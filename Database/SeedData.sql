/* ===========================================================
   SeedData.sql
   Seeds one Admin and one regular User.

   Password hashing approach:
   The API (Services/PasswordHasherService.cs) uses ASP.NET Core's
   PasswordHasher<T> (PBKDF2-HMACSHA256, 100,000 iterations, Identity
   v3 format: 0x01 marker | prf | iterCount | saltSize | salt | subkey).
   The hashes below are REAL, verified PasswordHasher<T> output for
   these plaintext passwords:
       admin  -> Admin@123
       user1  -> User@123
   Change these passwords after first login in a real deployment.
   =========================================================== */
USE LiveTrackingDb;
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Username = 'admin')
BEGIN
    INSERT INTO dbo.Users (Username, PasswordHash, FullName, Role, IsActive)
    VALUES ('admin', 'AQAAAAEAAYagAAAAECBLxu1Ecmmbv6LxYn5hTqLAykR3QHvX4pRf7lONDzwzoolVpEcn8E0+dsYLjsFVZQ==', 'System Administrator', 'Admin', 1);
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Username = 'user1')
BEGIN
    INSERT INTO dbo.Users (Username, PasswordHash, FullName, Role, IsActive)
    VALUES ('user1', 'AQAAAAEAAYagAAAAEHF428KAViGU5ir3bumOvU9w1YBcysqBBxyfJWOHa1ZdXyWta9rMiExsNR+ikr3MOQ==', 'Demo Driver', 'User', 1);
END
GO
