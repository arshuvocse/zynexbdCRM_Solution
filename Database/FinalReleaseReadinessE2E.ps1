# ==============================================================================
# FINAL RELEASE-READINESS VERIFICATION RUNNER
# Manager -> Employee -> Lead -> Follow-up -> KPI -> Productivity
# ==============================================================================

$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"
$sqlServer = "127.0.0.1"
$dbName = "LiveTrackingDB"

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " FINAL RELEASE-READINESS E2E VERIFICATION RUN" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

$stepResults = [ordered]@{}

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host "`n$Name..." -ForegroundColor Yellow
    try {
        & $Action
        Write-Host "   PASS: $Name" -ForegroundColor Green
        $stepResults[$Name] = "PASS"
    } catch {
        Write-Host "   FAIL: $Name - $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails) {
            Write-Host "   Details: $($_.ErrorDetails.Message)" -ForegroundColor Red
        }
        $stepResults[$Name] = "FAIL"
        # Unlike the other suites, this is a single linear business flow (each step depends on
        # the previous one's output), so one real failure genuinely blocks the rest - but we still
        # want a full report instead of an unhandled exception, so record it and stop cleanly.
        Write-Host "`n==================================================================" -ForegroundColor Red
        Write-Host " RELEASE-READINESS FLOW STOPPED EARLY - SEE FAILURE ABOVE" -ForegroundColor Red
        Write-Host "==================================================================" -ForegroundColor Red
        $stepResults | Format-Table -AutoSize
        exit 1
    }
}

$adminHeaders = $null
$userHeaders = $null
$lead = $null
$userId = 0

Run-Step -Name "1. Manager Login" -Action {
    $bAdmin = @{ username = "admin"; password = "User@123" } | ConvertTo-Json
    $rAdmin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $bAdmin -ContentType "application/json"
    $script:adminHeaders = @{ Authorization = "Bearer $($rAdmin.token)" }
    Write-Host "   -> Admin Logged In: UserId=$($rAdmin.userId), CompanyId=$($rAdmin.companyId)" -ForegroundColor Green
}

Run-Step -Name "2. Employee Login" -Action {
    $bUser = @{ username = "user2"; password = "User@123" } | ConvertTo-Json
    $rUser = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $bUser -ContentType "application/json"
    $script:userHeaders = @{ Authorization = "Bearer $($rUser.token)" }
    $script:userId = $rUser.userId
    Write-Host "   -> Employee Logged In: UserId=$($rUser.userId), CompanyId=$($rUser.companyId)" -ForegroundColor Green
}

Run-Step -Name "3. Manager Creates Lead" -Action {
    $bLead = @{
        leadName = "Release Candidate Gold Lead"
        contactPerson = "Engr. Monirul Islam"
        phone = "01788776655"
        email = "monirul@goldstandard.com"
        address = "Motijheel, Dhaka"
        leadStatus = "New Lead"
        estimatedValue = 850000
        remarks = "Final release verification candidate lead"
    } | ConvertTo-Json
    $script:lead = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads" -Method Post -Headers $adminHeaders -Body $bLead -ContentType "application/json"
    Write-Host "   -> Lead Created: LeadId=$($lead.leadId), Status='$($lead.leadStatus)'" -ForegroundColor Green
}

Run-Step -Name "4. Manager Assigns Lead to Employee" -Action {
    $bAssign = @{ newUserId = $userId; remarks = "Assigned for product final presentation" } | ConvertTo-Json
    $assigned = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$($lead.leadId)/assign" -Method Post -Headers $adminHeaders -Body $bAssign -ContentType "application/json"
    if ($assigned.assignedUserId -ne $userId) { throw "Assignment did not stick: expected $userId, got $($assigned.assignedUserId)" }
    Write-Host "   -> Assigned to UserId: $($assigned.assignedUserId)" -ForegroundColor Green
}

Run-Step -Name "5. Employee Follow-up & Next Date" -Action {
    $nextDate = (Get-Date).AddDays(3).ToString("yyyy-MM-dd")
    $bFu = @{ status = "Follow Up"; nextFollowUpDate = $nextDate; remarks = "Presented solution architecture" } | ConvertTo-Json
    $fu = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$($lead.leadId)/followup" -Method Post -Headers $userHeaders -Body $bFu -ContentType "application/json"
    Write-Host "   -> Follow-up Logged (ID: $($fu.followUpId)), NextDate: $nextDate" -ForegroundColor Green
}

Run-Step -Name "6. Employee Adds Internal Remark" -Action {
    $bRem = @{ remark = "Client signed preliminary scope document" } | ConvertTo-Json
    $rem = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$($lead.leadId)/remarks" -Method Post -Headers $userHeaders -Body $bRem -ContentType "application/json"
    Write-Host "   -> Remark Added (ID: $($rem.remarkId))" -ForegroundColor Green
}

Run-Step -Name "7. Employee Finalizes Status to Closed" -Action {
    $bStat1 = @{ status = "Interested"; remarks = "Procurement approved budget" } | ConvertTo-Json
    $null = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$($lead.leadId)/status" -Method Put -Headers $userHeaders -Body $bStat1 -ContentType "application/json"

    $bStat2 = @{ status = "Closed"; remarks = "Final commercial contract signed" } | ConvertTo-Json
    $closed = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$($lead.leadId)/status" -Method Put -Headers $userHeaders -Body $bStat2 -ContentType "application/json"
    if ($closed.leadStatus -ne "Closed") { throw "Final status is '$($closed.leadStatus)', expected 'Closed'" }
    Write-Host "   -> Final Status: $($closed.leadStatus)" -ForegroundColor Green
}

Run-Step -Name "8. Verify Employee Dynamic KPI" -Action {
    $kpi = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/kpi" -Headers $userHeaders
    $daily = $kpi | Where-Object { $_.periodType -eq "Daily" }
    if ($daily.followUpDone -le 0) { throw "Daily KPI followUpDone should reflect the follow-up just logged" }
    Write-Host "   -> Daily KPI: Target=$($daily.followUpTarget), Done=$($daily.followUpDone), Achieved=$($daily.followUpAchievementPercent)%" -ForegroundColor Green
}

Run-Step -Name "9. Verify Manager Productivity Report" -Action {
    $prod = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/productivity?periodType=Daily" -Headers $adminHeaders
    $u = $prod.items | Where-Object { $_.userId -eq $userId }
    if ($null -eq $u) { throw "Employee (UserId=$userId) not present in manager productivity report" }
    Write-Host "   -> Employee: $($u.employeeName), FollowUps: $($u.followUpDone)/$($u.followUpTarget), Achievement: $($u.achievementPercent)%" -ForegroundColor Green
}

Run-Step -Name "10. Direct SQL Server Assertions" -Action {
    # Individual single-column queries instead of splitting one fixed-width multi-column row -
    # avoids misalignment if any value's textual width ever shifts.
    $q = {
        param($col)
        $out = sqlcmd -S $sqlServer -U sa -P sa1234 -d $dbName -Q "SET NOCOUNT ON; SELECT $col FROM myonline_tbl_CRM_Leads WHERE LeadId = $($lead.leadId);" -h -1
        ($out.Trim() -split '\r?\n')[0].Trim()
    }
    $companyId = & $q "CompanyId"
    $createdBy = & $q "CreatedByUserId"
    $assignedTo = & $q "AssignedUserId"
    $status = & $q "LeadStatus"

    if ($companyId -ne "1" -or $createdBy -ne "1" -or $assignedTo -ne "$userId" -or $status -ne "Closed") {
        throw "SQL Server assertion failure: CompanyId=$companyId, CreatedBy=$createdBy, AssignedTo=$assignedTo, Status=$status"
    }
    Write-Host "   -> SQL Server Row Confirmed: LeadId=$($lead.leadId), CompanyId=$companyId, CreatedBy=$createdBy, AssignedTo=$assignedTo, Status=$status" -ForegroundColor Green
}

Write-Host "`n==================================================================" -ForegroundColor Green
Write-Host " CRITICAL RELEASE-READINESS E2E FLOW COMPLETED WITH 100% SUCCESS!" -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Green
$stepResults | Format-Table -AutoSize
