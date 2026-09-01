/* ===========================================================
   ExecuteAll.sql
   Runs all database scripts in order using sqlcmd :r includes.
   Run with:  sqlcmd -S <server> -E -i ExecuteAll.sql
   (Requires sqlcmd scripting mode; in SSMS enable "SQLCMD Mode"
   from the Query menu before executing.)
   =========================================================== */
:r .\CreateDatabase.sql
:r .\CreateTables.sql
:r .\CreateIndexes.sql
:r .\CreateStoredProcedures.sql
:r .\SeedData.sql

PRINT 'All Live Tracking database scripts executed successfully.';
GO

/* Manual order, if not using SQLCMD mode:
   1. CreateDatabase.sql
   2. CreateTables.sql
   3. CreateIndexes.sql
   4. CreateStoredProcedures.sql
   5. SeedData.sql
*/
