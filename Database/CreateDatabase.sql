/* ===========================================================
   CreateDatabase.sql
   Creates the LiveTrackingDb database if it does not exist.
   =========================================================== */
IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE name = N'LiveTrackingDb')
BEGIN
    CREATE DATABASE LiveTrackingDb;
END
GO
