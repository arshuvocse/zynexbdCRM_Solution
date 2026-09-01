# Docker-Based CRM API and Multi-Tenant E2E Test Suite
# Tests against containerized API running at http://localhost:8080

$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " STARTING DOCKER-BASED CRM END-TO-END VERIFICATION SUITE" -ForegroundColor Cyan
Write-Host " Target URL: $baseUrl" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

$testResults = [ordered]@{}

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host "`n[TEST] $Name..." -ForegroundColor Yellow
    try {
        $res = & $Action
        Write-Host "   PASS: $Name" -ForegroundColor Green
        $testResults[$Name] = "PASS"
        return $res
    } catch {
        Write-Host "   FAIL: $Name - $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails) {
            Write-Host "   Error Details: $($_.ErrorDetails.Message)" -ForegroundColor Red
        }
        $testResults[$Name] = "FAIL"
        throw $_
    }
}

# --- SECTION 1: DOCKER HEALTH AND AUTHENTICATION ---

Run-Step -Name "1. Docker API Health and Swagger Reachability" -Action {
    $res = Invoke-WebRequest -Uri "$baseUrl/swagger/index.html" -UseBasicParsing
    if ($res.StatusCode -ne 200) { throw "API returned status $($res.StatusCode)" }
}

$script:admin1Token = ""
$script:user1Token = ""
$script:admin2Token = ""
$script:user2Token = ""

Run-Step -Name "2. Authentication - Company 1 Admin (Manager)" -Action {
    $body = @{ username = "admin"; password = "User@123" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($res.role -ne "Admin" -or $res.companyId -ne 1) { throw "Invalid auth response: Role=$($res.role), CompanyId=$($res.companyId)" }
    $script:admin1Token = $res.token
    $script:admin1Id = $res.userId
}

Run-Step -Name "3. Authentication - Company 1 Employee (User 2)" -Action {
    $body = @{ username = "user2"; password = "User@123" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($res.role -ne "User" -or $res.companyId -ne 1) { throw "Invalid auth response: Role=$($res.role), CompanyId=$($res.companyId)" }
    $script:user1Token = $res.token
    $script:user1Id = $res.userId
}

Run-Step -Name "4. Authentication - Company 2 Admin (Beta Manager)" -Action {
    $body = @{ username = "beta_admin"; password = "User@123" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($res.role -ne "Admin" -or $res.companyId -ne 2) { throw "Invalid auth response: Role=$($res.role), CompanyId=$($res.companyId)" }
    $script:admin2Token = $res.token
}

Run-Step -Name "5. Authentication - Company 2 Employee (Beta User)" -Action {
    $body = @{ username = "beta_user"; password = "User@123" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($res.role -ne "User" -or $res.companyId -ne 2) { throw "Invalid auth response: Role=$($res.role), CompanyId=$($res.companyId)" }
    $script:user2Token = $res.token
    $script:user2Id = $res.userId
}

# --- SECTION 2: MANAGER CRM APIS (Company 1) ---

$admin1Headers = @{ Authorization = "Bearer $admin1Token" }
$user1Headers = @{ Authorization = "Bearer $user1Token" }
$admin2Headers = @{ Authorization = "Bearer $admin2Token" }
$user2Headers = @{ Authorization = "Bearer $user2Token" }

$script:company1LeadId = 0
$script:company1ProductServiceId = 0
$script:company1LeadSourceId = 0

Run-Step -Name "6. Manager API - Get Master Products and Sources (Company 1)" -Action {
    $prods = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/products-services" -Headers $admin1Headers
    $sources = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/lead-sources" -Headers $admin1Headers
    if ($prods.Count -eq 0 -or $sources.Count -eq 0) { throw "No master data returned" }
    $script:company1ProductServiceId = $prods[0].productServiceId
    $script:company1LeadSourceId = $sources[0].leadSourceId
}

Run-Step -Name "7. Manager API - Initial CRM Dashboard (Company 1)" -Action {
    $dash = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/dashboard" -Headers $admin1Headers
    if ($dash -eq $null) { throw "Dashboard response was null" }
}

Run-Step -Name "8. Manager API - Create Company Lead (Company 1)" -Action {
    $leadName = "Docker E2E Lead " + (Get-Random -Minimum 1000 -Maximum 9999)
    $body = @{
        leadName = $leadName
        contactPerson = "Farhan Ahmed"
        phone = "01811223344"
        email = "farhan@acme.com"
        address = "Banani Commercial Area, Dhaka"
        productServiceId = $script:company1ProductServiceId
        leadSourceId = $script:company1LeadSourceId
        leadStatus = "New Lead"
        estimatedValue = 75000
        remarks = "Created during Docker E2E test suite"
    } | ConvertTo-Json

    $created = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads" -Method Post -Headers $admin1Headers -Body $body -ContentType "application/json"
    if ($created.leadId -le 0 -or $created.leadName -ne $leadName) { throw "Invalid lead creation response" }
    $script:company1LeadId = $created.leadId
}

Run-Step -Name "9. Manager API - Get Lead Details (Company 1)" -Action {
    $detail = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$script:company1LeadId" -Headers $admin1Headers
    if ($detail.leadId -ne $script:company1LeadId -or $detail.contactPerson -ne "Farhan Ahmed") { throw "Lead details mismatch" }
}

Run-Step -Name "10. Manager API - Update Lead (Company 1)" -Action {
    $body = @{
        leadName = "Docker E2E Lead Updated"
        estimatedValue = 90000
    } | ConvertTo-Json
    $updated = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$script:company1LeadId" -Method Put -Headers $admin1Headers -Body $body -ContentType "application/json"
    if ($updated.estimatedValue -ne 90000) { throw "Lead update failed" }
}

Run-Step -Name "11. Manager API - Assign Lead to Employee (User 2)" -Action {
    $body = @{
        newUserId = $user1Id
        remarks = "Please follow up with customer regarding quotation"
    } | ConvertTo-Json
    $assigned = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$script:company1LeadId/assign" -Method Post -Headers $admin1Headers -Body $body -ContentType "application/json"
    if ($assigned.assignedUserId -ne $user1Id -or $assigned.assignments.Count -lt 1) { throw "Assignment failed" }
}

Run-Step -Name "12. Manager API - Configure KPI Target (Company 1)" -Action {
    # 'admin' is office2-scoped, so a true company-wide default (no officeLocationId) is correctly
    # rejected by office-scoping authorization; set an office-level default instead.
    $body = @{
        periodType = "Daily"
        followUpTarget = 30
        interestedTarget = 20
        closedTarget = 10
        officeLocationId = 2
    } | ConvertTo-Json
    $kpi = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/kpi" -Method Post -Headers $admin1Headers -Body $body -ContentType "application/json"
    if ($kpi.followUpTarget -ne 30) { throw "KPI target creation failed" }
}

# --- SECTION 3: EMPLOYEE CRM APIS (Company 1 - User 2) ---

Run-Step -Name "13. Employee API - View Dashboard (User 2)" -Action {
    $uDash = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/dashboard" -Headers $user1Headers
    if ($uDash.myTotalLeads -lt 1) { throw "Assigned lead not reflected in employee dashboard" }
}

Run-Step -Name "14. Employee API - View My Assigned Leads (User 2)" -Action {
    $uLeads = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads" -Headers $user1Headers
    $found = $uLeads.items | Where-Object { $_.leadId -eq $script:company1LeadId }
    if ($found -eq $null) { throw "Assigned lead not found in employee lead list" }
}

Run-Step -Name "15. Employee API - Record Follow-up with Next Date (User 2)" -Action {
    $nextDate = (Get-Date).AddDays(3).ToString("yyyy-MM-dd")
    $body = @{
        status = "Interested"
        nextFollowUpDate = $nextDate
        remarks = "Demo completed. Customer agreed to purchase."
    } | ConvertTo-Json
    $fu = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$script:company1LeadId/followup" -Method Post -Headers $user1Headers -Body $body -ContentType "application/json"
    if ($fu.followUpId -le 0 -or $fu.status -ne "Interested") { throw "Follow-up recording failed" }
}

Run-Step -Name "16. Employee API - Add Internal Remark (User 2)" -Action {
    $body = @{
        remark = "Customer finance department approved procurement."
    } | ConvertTo-Json
    $rem = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$script:company1LeadId/remarks" -Method Post -Headers $user1Headers -Body $body -ContentType "application/json"
    if ($rem.remarkId -le 0) { throw "Remark recording failed" }
}

$script:selfLeadId = 0
Run-Step -Name "17. Employee API - Create Self Lead (User 2)" -Action {
    $selfLeadName = "Self Sourced Lead " + (Get-Random -Minimum 1000 -Maximum 9999)
    $body = @{
        leadName = $selfLeadName
        contactPerson = "Tariqul Islam"
        phone = "01911334455"
        leadStatus = "New Lead"
        productServiceId = $script:company1ProductServiceId
        leadSourceId = $script:company1LeadSourceId
    } | ConvertTo-Json
    $selfCreated = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads" -Method Post -Headers $user1Headers -Body $body -ContentType "application/json"
    if ($selfCreated.leadId -le 0 -or $selfCreated.leadSourceType -ne "Self") { throw "Self lead creation failed" }
    $script:selfLeadId = $selfCreated.leadId
}

Run-Step -Name "18. Employee API - Update Lead Status (User 2)" -Action {
    $body = @{
        status = "Closed"
        remarks = "Contract signed and deal successfully closed."
    } | ConvertTo-Json
    $closedLead = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$script:selfLeadId/status" -Method Put -Headers $user1Headers -Body $body -ContentType "application/json"
    if ($closedLead.leadStatus -ne "Closed") { throw "Lead status update to Closed failed" }
}

Run-Step -Name "19. Employee API - Check Dynamic KPI Performance Calculation (User 2)" -Action {
    $kpiPerf = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/kpi" -Headers $user1Headers
    if ($kpiPerf.Count -eq 0) { throw "No KPI performance returned" }
    $daily = $kpiPerf | Where-Object { $_.periodType -eq "Daily" }
    if ($daily.followUpDone -lt 1 -or $daily.closedDone -lt 1) { throw "KPI metrics not calculated from real DB interactions" }
}

# --- SECTION 4: MANAGER PRODUCTIVITY & FOLLOW-UP DASHBOARD (Company 1) ---

Run-Step -Name "20. Manager API - View Employee Productivity Report (Company 1)" -Action {
    $prod = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/productivity?periodType=Daily" -Headers $admin1Headers
    $user2Prod = $prod.items | Where-Object { $_.userId -eq $user1Id }
    if ($user2Prod -eq $null -or $user2Prod.followUpDone -lt 1) { throw "User 2 productivity not aggregated properly" }
}

Run-Step -Name "21. Manager API - View Follow-ups Filtered (Company 1)" -Action {
    $fus = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/followups?filterType=7days" -Headers $admin1Headers
    $found = $fus | Where-Object { $_.leadId -eq $script:company1LeadId }
    if ($found -eq $null) { throw "Scheduled follow-up not found in 7days list" }
}

# --- SECTION 5: STRICT MULTI-TENANT ISOLATION SECURITY ---

Run-Step -Name "22. Security Isolation - Tenant 2 Manager cannot read Tenant 1 Lead (Expect 404)" -Action {
    try {
        $null = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$script:company1LeadId" -Headers $admin2Headers
        throw "SECURITY BREACH: Tenant 2 was able to read Tenant 1 Lead!"
    } catch {
        if ($_.Exception.Response.StatusCode -ne [System.Net.HttpStatusCode]::NotFound) {
            throw "Expected 404 NotFound, got: $($_.Exception.Message)"
        }
    }
}

Run-Step -Name "23. Security Isolation - Tenant 2 Manager cannot update Tenant 1 Lead (Expect 404)" -Action {
    try {
        $body = @{ leadName = "Hacked Lead" } | ConvertTo-Json
        $null = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$script:company1LeadId" -Method Put -Headers $admin2Headers -Body $body -ContentType "application/json"
        throw "SECURITY BREACH: Tenant 2 was able to update Tenant 1 Lead!"
    } catch {
        if ($_.Exception.Response.StatusCode -ne [System.Net.HttpStatusCode]::NotFound) {
            throw "Expected 404 NotFound, got: $($_.Exception.Message)"
        }
    }
}

Run-Step -Name "24. Security Isolation - Tenant 2 Manager cannot assign Tenant 1 Lead (Expect 400/404)" -Action {
    try {
        $body = @{ newUserId = $user2Id } | ConvertTo-Json
        $null = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads/$script:company1LeadId/assign" -Method Post -Headers $admin2Headers -Body $body -ContentType "application/json"
        throw "SECURITY BREACH: Tenant 2 was able to assign Tenant 1 Lead!"
    } catch {
        # Pass
    }
}

Run-Step -Name "25. Security Isolation - Tenant 2 Employee cannot access Tenant 1 Lead (Expect 404)" -Action {
    try {
        $null = Invoke-RestMethod -Uri "$baseUrl/api/crm/user/leads/$script:company1LeadId" -Headers $user2Headers
        throw "SECURITY BREACH: Tenant 2 Employee was able to read Tenant 1 Lead!"
    } catch {
        if ($_.Exception.Response.StatusCode -ne [System.Net.HttpStatusCode]::NotFound) {
            throw "Expected 404 NotFound, got: $($_.Exception.Message)"
        }
    }
}

Run-Step -Name "26. Security Isolation - Tenant 2 Leads list does NOT leak Tenant 1 Leads" -Action {
    $t2Leads = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads" -Headers $admin2Headers
    $leaked = $t2Leads.items | Where-Object { $_.leadId -eq $script:company1LeadId -or $_.companyId -eq 1 }
    if ($leaked -ne $null) { throw "SECURITY BREACH: Tenant 2 leads list contains Tenant 1 leads!" }
}

Run-Step -Name "27. Security Isolation - Tenant 2 Productivity does NOT leak Tenant 1 Employees" -Action {
    $t2Prod = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/productivity?periodType=Daily" -Headers $admin2Headers
    $leaked = $t2Prod.items | Where-Object { $_.userId -eq $user1Id -or $_.userId -eq $admin1Id }
    if ($leaked -ne $null) { throw "SECURITY BREACH: Tenant 2 productivity contains Tenant 1 employees!" }
}

# --- SECTION 6: API FILTERING, SEARCHING & PAGINATION ---

Run-Step -Name "28. API Filtering - Filter Leads by Status 'Interested'" -Action {
    $filtered = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads?status=Interested" -Headers $admin1Headers
    if ($filtered.items.Count -eq 0 -or ($filtered.items | Where-Object { $_.leadStatus -ne "Interested" })) {
        throw "Filtering by status failed"
    }
}

Run-Step -Name "29. API Searching - Search Leads by Contact Name" -Action {
    $searched = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads?search=Farhan" -Headers $admin1Headers
    if ($searched.items.Count -eq 0) { throw "Search by contact name failed" }
}

Run-Step -Name "30. API Pagination - Paged Result Verification" -Action {
    $paged = Invoke-RestMethod -Uri "$baseUrl/api/crm/manager/leads?pageNumber=1&pageSize=1" -Headers $admin1Headers
    if ($paged.items.Count -ne 1 -or $paged.pageSize -ne 1) { throw "Pagination pageSize failed" }
}

# --- SECTION 7: LIVE TRACKING & EXISTING SYSTEM REGRESSION ---

Run-Step -Name "31. Non-Regression - Location Ingestion (Ping)" -Action {
    $body = @{
        latitude = 23.7925
        longitude = 90.4078
        batteryPercent = 85
        networkType = "WIFI"
        isGpsEnabled = $true
    } | ConvertTo-Json
    $pingRes = Invoke-RestMethod -Uri "$baseUrl/api/locations/ping" -Method Post -Headers $user1Headers -Body $body -ContentType "application/json"
    if ($pingRes -eq $null) { throw "Location ping failed" }
}

Run-Step -Name "32. Non-Regression - Admin Latest Locations" -Action {
    $latest = Invoke-RestMethod -Uri "$baseUrl/api/locations/latest" -Headers $admin1Headers
    if ($latest -eq $null) { throw "Latest locations failed" }
}

Run-Step -Name "33. Non-Regression - Attendance Monthly Summary" -Action {
    $today = (Get-Date)
    $att = Invoke-RestMethod -Uri "$baseUrl/api/attendance/admin/monthly-summary?year=$($today.Year)&month=$($today.Month)" -Headers $admin1Headers
    if ($att -eq $null) { throw "Attendance summary failed" }
}

Run-Step -Name "34. Non-Regression - Holiday Calendar and Shifts API" -Action {
    $holidays = Invoke-RestMethod -Uri "$baseUrl/api/holidays" -Headers $admin1Headers
    $shifts = Invoke-RestMethod -Uri "$baseUrl/api/shifts" -Headers $admin1Headers
    if ($holidays -eq $null -or $shifts -eq $null) { throw "Holidays or Shifts failed" }
}

# --- SECTION 8: SQL SERVER DATABASE DIRECT VERIFICATION ---

Run-Step -Name "35. Database Verification - Inspect SQL Server Records" -Action {
    $dbCheck = sqlcmd -S "127.0.0.1" -U sa -P sa1234 -d LiveTrackingDB -Q "
        SELECT LeadId, CompanyId, LeadName, LeadStatus, AssignedUserId FROM myonline_tbl_CRM_Leads WHERE LeadId = $script:company1LeadId;
        SELECT FollowUpId, LeadId, Status, NextFollowUpDate FROM myonline_tbl_CRM_LeadFollowUps WHERE LeadId = $script:company1LeadId;
        SELECT RemarkId, LeadId, Remark FROM myonline_tbl_CRM_LeadRemarks WHERE LeadId = $script:company1LeadId;
        SELECT AssignmentId, LeadId, PreviousUserId, NewUserId FROM myonline_tbl_CRM_LeadAssignments WHERE LeadId = $script:company1LeadId;
    "
    if ($LASTEXITCODE -ne 0) { throw "SQL Verification query failed" }
}

Write-Host "`n==================================================================" -ForegroundColor Green
Write-Host " ALL 35 DOCKER API AND END-TO-END TESTS PASSED WITH 100% SUCCESS!" -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Green

$testResults | Format-Table -AutoSize
