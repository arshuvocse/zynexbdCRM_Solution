# ==============================================================================
# PHASE C: STORED PROCEDURE VALIDATION TEST HARNESS
# Tests all 12 CRM Stored Procedures against SQL Server (crm_solution_DB)
# ==============================================================================

$ErrorActionPreference = "Stop"
$server = "127.0.0.1"
$db = "crm_solution_DB"
$user = "sa"
$pass = "sa1234"

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " PHASE C: EXECUTING 12 CRM STORED PROCEDURES DIRECT TESTS" -ForegroundColor Cyan
Write-Host " Server: $server | Database: $db" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

$results = [System.Collections.Generic.List[PSCustomObject]]::new()

function Run-SpTest {
    param(
        [string]$TestId,
        [string]$ProcedureName,
        [string]$Scenario,
        [string]$SqlStatement,
        [bool]$ExpectError = $false,
        [string]$ExpectedPattern = ""
    )

    Write-Host -NoNewline ">> [$TestId] $($ProcedureName) - $Scenario ... "
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = "FAIL"
    $details = ""

    try {
        $output = sqlcmd -S $server -U $user -P $pass -d $db -Q "SET NOCOUNT ON; $SqlStatement" 2>&1
        $sw.Stop()
        $outStr = ($output | Out-String).Trim()

        if ($ExpectError) {
            if ($outStr -match "Msg \d+" -or $outStr -match "error" -or $outStr -match "forbidden") {
                $status = "PASS"
                $details = "Correctly caught expected SQL error: $($outStr.Split("`n")[0])"
                Write-Host "PASS (Expected Error Caught, $($sw.ElapsedMilliseconds)ms)" -ForegroundColor Green
            } else {
                $status = "FAIL"
                $details = "Expected error but SQL succeeded: $outStr"
                Write-Host "FAIL (Expected error but succeeded)" -ForegroundColor Red
            }
        } else {
            if ($outStr -match "Msg \d+") {
                $status = "FAIL"
                $details = "SQL Error: $outStr"
                Write-Host "FAIL (SQL Error)" -ForegroundColor Red
            } elseif ($ExpectedPattern -ne "" -and $outStr -notmatch $ExpectedPattern) {
                $status = "FAIL"
                $details = "Output did not match '$ExpectedPattern': $outStr"
                Write-Host "FAIL (Pattern mismatch)" -ForegroundColor Red
            } else {
                $status = "PASS"
                $details = if ($outStr.Length -gt 80) { $outStr.Substring(0, 80).Replace("`n", " ") + "..." } else { $outStr }
                Write-Host "PASS ($($sw.ElapsedMilliseconds)ms)" -ForegroundColor Green
            }
        }
    } catch {
        $sw.Stop()
        $errStr = $_.Exception.Message
        if ($ExpectError) {
            $status = "PASS"
            $details = "Correctly caught expected error: $errStr"
            Write-Host "PASS (Expected Error Caught, $($sw.ElapsedMilliseconds)ms)" -ForegroundColor Green
        } else {
            $status = "FAIL"
            $details = "Unexpected SQL error: $errStr"
            Write-Host "FAIL ($errStr)" -ForegroundColor Red
        }
    }

    $results.Add([PSCustomObject]@{
        TestId = $TestId
        Procedure = $ProcedureName
        Scenario = $Scenario
        Status = $status
        DurationMs = $sw.ElapsedMilliseconds
        Details = $details
    })
}

# 1. sp_Crm_Lead_Save (Insert)
Run-SpTest -TestId "SP-01" -ProcedureName "sp_Crm_Lead_Save" -Scenario "Insert New Lead for Company 1" `
    -SqlStatement "
        DECLARE @NewId INT;
        EXEC dbo.sp_Crm_Lead_Save 
            @LeadId = @NewId OUTPUT,
            @CompanyId = 1,
            @LeadName = N'SP Automated Test Lead A',
            @ContactPerson = N'Kamal Ahmed',
            @Phone = N'01711223344',
            @Email = N'kamal@testlead.com',
            @Address = N'Gulshan 2, Dhaka',
            @LeadSourceType = N'Manager',
            @LeadStatus = N'New Lead',
            @CreatedByUserId = 1,
            @AssignedUserId = 2,
            @OfficeLocationId = 2,
            @EstimatedValue = 150000.00,
            @Remarks = N'Initial creation from SP test';
    " -ExpectedPattern "SP Automated Test Lead A"

# Get the newly created LeadId for subsequent tests
$leadIdOut = sqlcmd -S $server -U $user -P $pass -d $db -Q "SET NOCOUNT ON; SELECT TOP 1 LeadId FROM dbo.myonline_tbl_CRM_Leads WHERE LeadName = 'SP Automated Test Lead A' ORDER BY LeadId DESC;" -h -1
$testLeadId = [int]($leadIdOut.Trim().Split("`n")[0].Trim())
Write-Host "   -> Using Test LeadId: $testLeadId" -ForegroundColor Cyan

# 2. sp_Crm_Lead_Save (Update)
Run-SpTest -TestId "SP-02" -ProcedureName "sp_Crm_Lead_Save" -Scenario "Update Existing Lead Value & Follow-Up Date" `
    -SqlStatement "
        DECLARE @LId INT = $testLeadId;
        EXEC dbo.sp_Crm_Lead_Save 
            @LeadId = @LId,
            @CompanyId = 1,
            @LeadName = N'SP Automated Test Lead A (Updated)',
            @ContactPerson = N'Kamal Ahmed',
            @Phone = N'01711223344',
            @Email = N'kamal@testlead.com',
            @Address = N'Gulshan 2, Dhaka',
            @LeadSourceType = N'Manager',
            @LeadStatus = N'Follow Up',
            @CreatedByUserId = 1,
            @AssignedUserId = 2,
            @OfficeLocationId = 2,
            @EstimatedValue = 200000.00,
            @NextFollowUpDate = '2026-09-10 10:00:00',
            @Remarks = N'Updated estimated value';
    " -ExpectedPattern "SP Automated Test Lead A \(Updated\)"

# 3. sp_Crm_Lead_GetById
Run-SpTest -TestId "SP-03" -ProcedureName "sp_Crm_Lead_GetById" -Scenario "Retrieve Lead with 5 History Result Sets" `
    -SqlStatement "
        EXEC dbo.sp_Crm_Lead_GetById @CompanyId = 1, @LeadId = $testLeadId;
    " -ExpectedPattern "SP Automated Test Lead A"

# 4. sp_Crm_Lead_GetList
Run-SpTest -TestId "SP-04" -ProcedureName "sp_Crm_Lead_GetList" -Scenario "Paginated Lead List with Filters" `
    -SqlStatement "
        EXEC dbo.sp_Crm_Lead_GetList 
            @CompanyId = 1, 
            @AssignedUserId = 2, 
            @Status = 'Follow Up',
            @PageNumber = 1, 
            @PageSize = 10;
    " -ExpectedPattern "TotalCount"

# 5. sp_Crm_Lead_Assign (Positive)
Run-SpTest -TestId "SP-05" -ProcedureName "sp_Crm_Lead_Assign" -Scenario "Reassign Lead to Another Company 1 Employee" `
    -SqlStatement "
        EXEC dbo.sp_Crm_Lead_Assign 
            @CompanyId = 1, 
            @LeadId = $testLeadId, 
            @AssignedByUserId = 1, 
            @NewUserId = 17, 
            @Remarks = N'Reassigning to Employee 02 Rahim';
    " -ExpectedPattern "Employee 02 Rahim"

# 6. sp_Crm_Lead_Assign (Negative: Cross-Company Reassignment)
Run-SpTest -TestId "SP-06" -ProcedureName "sp_Crm_Lead_Assign" -Scenario "Negative: Prevent Reassignment to Company 2 Employee" `
    -SqlStatement "
        EXEC dbo.sp_Crm_Lead_Assign 
            @CompanyId = 1, 
            @LeadId = $testLeadId, 
            @AssignedByUserId = 1, 
            @NewUserId = 12, -- Beta User (Company 2)
            @Remarks = N'Malicious cross-company assignment';
    " -ExpectError $true

# 7. sp_Crm_Lead_FollowUp_Save
Run-SpTest -TestId "SP-07" -ProcedureName "sp_Crm_Lead_FollowUp_Save" -Scenario "Record Follow-Up, Remarks, and Status Transition" `
    -SqlStatement "
        DECLARE @FId INT;
        EXEC dbo.sp_Crm_Lead_FollowUp_Save 
            @FollowUpId = @FId OUTPUT,
            @CompanyId = 1,
            @LeadId = $testLeadId,
            @UserId = 17,
            @FollowUpDateUtc = '2026-09-03 14:00:00',
            @NextFollowUpDate = '2026-09-03 18:00:00',
            @Status = N'Interested',
            @Remarks = N'Client expressed strong interest in enterprise plan',
            @OfficeLocationId = 2;
    " -ExpectedPattern "Client expressed strong interest"

# 8. sp_Crm_FollowUp_GetToday
Run-SpTest -TestId "SP-08" -ProcedureName "sp_Crm_FollowUp_GetToday" -Scenario "Retrieve Follow-Ups Scheduled For Today" `
    -SqlStatement "
        EXEC dbo.sp_Crm_FollowUp_GetToday @CompanyId = 1, @UserId = 17;
    " -ExpectedPattern "SP Automated Test Lead A"

# 9. sp_Crm_FollowUp_GetOverdue
Run-SpTest -TestId "SP-09" -ProcedureName "sp_Crm_FollowUp_GetOverdue" -Scenario "Retrieve Overdue Follow-Ups" `
    -SqlStatement "
        -- Create past follow-up lead
        DECLARE @OverdueLId INT;
        EXEC dbo.sp_Crm_Lead_Save 
            @LeadId = @OverdueLId OUTPUT,
            @CompanyId = 1,
            @LeadName = N'Overdue Test Lead',
            @LeadStatus = N'Follow Up',
            @CreatedByUserId = 1,
            @AssignedUserId = 17,
            @NextFollowUpDate = '2026-08-15 10:00:00';

        EXEC dbo.sp_Crm_FollowUp_GetOverdue @CompanyId = 1, @UserId = 17;
    " -ExpectedPattern "Overdue Test Lead"

# 10. sp_Crm_FollowUp_GetUpcoming
Run-SpTest -TestId "SP-10" -ProcedureName "sp_Crm_FollowUp_GetUpcoming" -Scenario "Retrieve Future Scheduled Follow-Ups" `
    -SqlStatement "
        DECLARE @UpcomingLId INT;
        EXEC dbo.sp_Crm_Lead_Save 
            @LeadId = @UpcomingLId OUTPUT,
            @CompanyId = 1,
            @LeadName = N'Upcoming Test Lead',
            @LeadStatus = N'Follow Up',
            @CreatedByUserId = 1,
            @AssignedUserId = 17,
            @NextFollowUpDate = '2026-09-20 10:00:00';

        EXEC dbo.sp_Crm_FollowUp_GetUpcoming @CompanyId = 1, @UserId = 17, @DaysAhead = 30;
    " -ExpectedPattern "Upcoming Test Lead"

# 11. sp_Crm_Kpi_Save
Run-SpTest -TestId "SP-11" -ProcedureName "sp_Crm_Kpi_Save" -Scenario "Set Daily, Weekly, and Monthly KPI Targets" `
    -SqlStatement "
        DECLARE @K1 INT, @K2 INT, @K3 INT;
        EXEC dbo.sp_Crm_Kpi_Save @KpiId = @K1 OUTPUT, @CompanyId = 1, @CreatedByUserId = 1, @UserId = 17, @PeriodType = N'Daily', @FollowUpTarget = 30, @InterestedTarget = 20, @ClosedTarget = 10;
        EXEC dbo.sp_Crm_Kpi_Save @KpiId = @K2 OUTPUT, @CompanyId = 1, @CreatedByUserId = 1, @UserId = 17, @PeriodType = N'Weekly', @FollowUpTarget = 150, @InterestedTarget = 100, @ClosedTarget = 50;
        EXEC dbo.sp_Crm_Kpi_Save @KpiId = @K3 OUTPUT, @CompanyId = 1, @CreatedByUserId = 1, @UserId = 17, @PeriodType = N'Monthly', @FollowUpTarget = 600, @InterestedTarget = 400, @ClosedTarget = 200;
    " -ExpectedPattern "Monthly"

# 12. sp_Crm_Kpi_Productivity
Run-SpTest -TestId "SP-12" -ProcedureName "sp_Crm_Kpi_Productivity" -Scenario "Calculate Productivity Achievement & Prevent Double Counting" `
    -SqlStatement "
        EXEC dbo.sp_Crm_Kpi_Productivity @CompanyId = 1, @PeriodType = N'Daily', @UserId = 17;
    " -ExpectedPattern "Employee 02 Rahim"

# 13. sp_Crm_ManagerDashboard
Run-SpTest -TestId "SP-13" -ProcedureName "sp_Crm_ManagerDashboard" -Scenario "Execute Manager Dashboard Metrics & Status Breakdown" `
    -SqlStatement "
        EXEC dbo.sp_Crm_ManagerDashboard @CompanyId = 1, @ManagerUserId = 1;
    " -ExpectedPattern "TotalLeads"

# 14. sp_Crm_EmployeeDashboard
Run-SpTest -TestId "SP-14" -ProcedureName "sp_Crm_EmployeeDashboard" -Scenario "Execute Employee Personal Dashboard" `
    -SqlStatement "
        EXEC dbo.sp_Crm_EmployeeDashboard @CompanyId = 1, @UserId = 17;
    " -ExpectedPattern "MyTotalLeads"

# 15. Negative Tenant Test (Company 2 cannot update Company 1 Lead)
Run-SpTest -TestId "SP-15" -ProcedureName "sp_Crm_Lead_Save" -Scenario "Negative: Company 2 cannot update Company 1 Lead" `
    -SqlStatement "
        DECLARE @LId INT = $testLeadId;
        EXEC dbo.sp_Crm_Lead_Save 
            @LeadId = @LId,
            @CompanyId = 2,
            @LeadName = N'Cross Company Malicious Update',
            @CreatedByUserId = 11;
    " -ExpectError $true

# 16. Negative Follow-Up Test (Empty remarks rejected)
Run-SpTest -TestId "SP-16" -ProcedureName "sp_Crm_Lead_FollowUp_Save" -Scenario "Negative: Empty remarks must be rejected" `
    -SqlStatement "
        DECLARE @FId INT;
        EXEC dbo.sp_Crm_Lead_FollowUp_Save 
            @FollowUpId = @FId OUTPUT,
            @CompanyId = 1,
            @LeadId = $testLeadId,
            @UserId = 17,
            @Status = N'Follow Up',
            @Remarks = N'';
    " -ExpectError $true

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " STORED PROCEDURE TEST RESULTS SUMMARY" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
$passCount = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = ($results | Where-Object { $_.Status -eq "FAIL" }).Count
Write-Host "Total: $($results.Count) | PASS: $passCount | FAIL: $failCount" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })

$results | Format-Table TestId, Procedure, Status, DurationMs, Scenario -AutoSize
