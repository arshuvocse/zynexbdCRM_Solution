-- =============================================
-- Database Backup Script: LiveTrackingDB
-- =============================================
DECLARE @BackupDir NVARCHAR(255) = N'C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER2019\MSSQL\Backup\';
DECLARE @FileName NVARCHAR(255) = @BackupDir + N'LiveTrackingDB_' + REPLACE(REPLACE(REPLACE(CONVERT(NVARCHAR(20), GETDATE(), 120), '-', ''), ' ', '_'), ':', '') + N'.bak';

PRINT 'Backing up LiveTrackingDB to ' + @FileName;

BACKUP DATABASE [LiveTrackingDB]
TO DISK = @FileName
WITH FORMAT,
     MEDIANAME = 'LiveTrackingDB_Backups',
     NAME = 'Full Backup of LiveTrackingDB';

PRINT 'Database backup completed successfully.';
