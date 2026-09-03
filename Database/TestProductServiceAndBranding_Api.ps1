# ==============================================================================
# TEST PRODUCT/SERVICE AND DYNAMIC COMPANY BRANDING API
# Validates Multi-Tenant Isolation, Duplicate Prevention, Role Authorization,
# Soft Inactivation, and Dynamic Logo/Branding Loading
# ==============================================================================

param(
    [string]$baseUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Continue"

Write-Host "=================================================================="
Write-Host " PRODUCT/SERVICE & DYNAMIC BRANDING MULTI-TENANT TEST SUITE"
Write-Host " Target API: $baseUrl"
Write-Host "=================================================================="

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body = $null
    )
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $uri = "$baseUrl$Path"

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $json = if ($Body) { $Body | ConvertTo-Json -Depth 10 } else { $null }
        $res = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body $json -ErrorAction Stop
        $sw.Stop()
        return @{ Success = $true; StatusCode = 200; Data = $res; DurationMs = $sw.ElapsedMilliseconds }
    }
    catch {
        $sw.Stop()
        $status = 0
        $bodyText = ""
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $bodyText = $reader.ReadToEnd()
        }
        return @{ Success = $false; StatusCode = $status; Error = $_.Exception.Message; Body = $bodyText; DurationMs = $sw.ElapsedMilliseconds }
    }
}

# 1. Authenticate Tokens
Write-Host "`n--- [1. AUTHENTICATION] ---"
$loginC1Admin = Invoke-Api -Method "POST" -Path "/api/auth/login" -Body @{ Username = "admin"; Password = "Admin@123" }
$c1AdminToken = $loginC1Admin.Data.token
Write-Host "C1 Admin Token acquired: $($loginC1Admin.Data.username), Company: $($loginC1Admin.Data.companyName), Logo: $($loginC1Admin.Data.companyLogoUrl)"

$loginC1User = Invoke-Api -Method "POST" -Path "/api/auth/login" -Body @{ Username = "user2"; Password = "User@123" }
$c1UserToken = $loginC1User.Data.token
Write-Host "C1 User Token acquired: $($loginC1User.Data.username), Company: $($loginC1User.Data.companyName)"

$loginC2Admin = Invoke-Api -Method "POST" -Path "/api/auth/login" -Body @{ Username = "beta_admin"; Password = "User@123" }
$c2AdminToken = $loginC2Admin.Data.token
Write-Host "C2 Admin Token acquired: $($loginC2Admin.Data.username), Company: $($loginC2Admin.Data.companyName), Logo: $($loginC2Admin.Data.companyLogoUrl)"

$loginC2User = Invoke-Api -Method "POST" -Path "/api/auth/login" -Body @{ Username = "beta_user"; Password = "User@123" }
$c2UserToken = $loginC2User.Data.token
Write-Host "C2 User Token acquired: $($loginC2User.Data.username), Company: $($loginC2User.Data.companyName)"

$tests = @()

function Record-Test ($id, $desc, $pass, $ms, $notes = "") {
    $status = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host ">> [$id] $desc ... $status (${ms}ms) $notes"
    $script:tests += [PSCustomObject]@{
        Id = $id
        Scenario = $desc
        Status = $status
        DurationMs = $ms
        Notes = $notes
    }
}

Write-Host "`n--- [2. DYNAMIC COMPANY BRANDING] ---"
# T01: C1 Branding
$resT01 = Invoke-Api -Method "GET" -Path "/api/crm/company/branding" -Token $c1AdminToken
$t01Pass = $resT01.Success -and ($resT01.Data.data.companyName -eq "Default Organization") -and ($resT01.Data.data.logoUrl -like "*company1_logo.png*")
Record-Test "BRD-01" "C1 Dynamic Branding API" $t01Pass $resT01.DurationMs "Logo: $($resT01.Data.data.logoUrl)"

# T02: C2 Branding
$resT02 = Invoke-Api -Method "GET" -Path "/api/crm/company/branding" -Token $c2AdminToken
$t02Pass = $resT02.Success -and ($resT02.Data.data.companyName -eq "Beta Solutions Inc") -and ($resT02.Data.data.logoUrl -like "*company2_logo.png*")
Record-Test "BRD-02" "C2 Dynamic Branding API" $t02Pass $resT02.DurationMs "Logo: $($resT02.Data.data.logoUrl)"

# T03: Cross-Tenant Branding Isolation
$resT03 = Invoke-Api -Method "GET" -Path "/api/crm/company/branding" -Token $c2UserToken
$t03Pass = $resT03.Success -and ($resT03.Data.data.companyId -eq 2) -and ($resT03.Data.data.companyName -ne "Default Organization")
Record-Test "BRD-03" "Cross-Tenant Branding Isolation" $t03Pass $resT03.DurationMs "Isolated to Company 2"

# T04: Static Logo Physical HTTP Retrieval
$logo1Uri = $resT01.Data.data.logoUrl
$resLogo1 = Invoke-WebRequest -Uri $logo1Uri -Method Get -UseBasicParsing
$t04Pass = ($resLogo1.StatusCode -eq 200) -and ($resLogo1.Headers["Content-Type"] -like "*image/png*")
Record-Test "BRD-04" "Physical Logo HTTP 200 Image/PNG Delivery" $t04Pass 15 "Bytes: $($resLogo1.RawContentLength)"

Write-Host "`n--- [3. PRODUCT/SERVICE CRUD & DUPLICATE VALIDATION] ---"
$uniqueSuffix = [System.DateTime]::UtcNow.Ticks.ToString().Substring(12)
$testProdName = "AI Analytics Suite $uniqueSuffix"

# T05: C1 Admin Creates Product
$resT05 = Invoke-Api -Method "POST" -Path "/api/crm/products-services" -Token $c1AdminToken -Body @{
    Name = $testProdName
    Code = "AI-ANL"
    Description = "Enterprise Predictive Analytics"
    Price = 35000.00
}
$c1ProdId = if ($resT05.Success) { $resT05.Data.data.productServiceId } else { 0 }
$t05Pass = $resT05.Success -and ($c1ProdId -gt 0)
Record-Test "PRD-01" "C1 Admin Creates Product/Service" $t05Pass $resT05.DurationMs "ProductId: $c1ProdId"

# T06: C1 Duplicate Prevention (Same Company + Same Name)
$resT06 = Invoke-Api -Method "POST" -Path "/api/crm/products-services" -Token $c1AdminToken -Body @{
    Name = $testProdName
    Code = "AI-ANL-DUP"
}
$t06Pass = (!$resT06.Success) -and ($resT06.StatusCode -eq 400) -and ($resT06.Body -like "*already exists*")
Record-Test "PRD-02" "Prevent Duplicate Product Name within Same Tenant" $t06Pass $resT06.DurationMs "Correctly rejected (400)"

# T07: Cross-Tenant Same Name Allowed (Company 2 can use same name)
$resT07 = Invoke-Api -Method "POST" -Path "/api/crm/products-services" -Token $c2AdminToken -Body @{
    Name = $testProdName
    Code = "AI-ANL-C2"
    Price = 40000.00
}
$c2ProdId = if ($resT07.Success) { $resT07.Data.data.productServiceId } else { 0 }
$t07Pass = $resT07.Success -and ($c2ProdId -gt 0) -and ($c2ProdId -ne $c1ProdId)
Record-Test "PRD-03" "Allow Same Name Across Different Tenants" $t07Pass $resT07.DurationMs "C2 ProductId: $c2ProdId"

# T08: Tenant Isolation in Query (C1 cannot see C2 Product, and vice-versa)
$resT08C1 = Invoke-Api -Method "GET" -Path "/api/crm/products-services?search=$uniqueSuffix" -Token $c1AdminToken
$resT08C2 = Invoke-Api -Method "GET" -Path "/api/crm/products-services?search=$uniqueSuffix" -Token $c2AdminToken
$c1Matches = @($resT08C1.Data.data.items) | Where-Object { $_.productServiceId -eq $c1ProdId }
$c2MatchesInC1 = @($resT08C1.Data.data.items) | Where-Object { $_.productServiceId -eq $c2ProdId }
$t08Pass = (@($c1Matches).Count -ge 1) -and (@($c2MatchesInC1).Count -eq 0)
Record-Test "PRD-04" "Tenant Query Isolation (Zero Cross-Company Leaks)" $t08Pass $resT08C1.DurationMs "C1 saw $(@($c1Matches).Count), leaked $(@($c2MatchesInC1).Count)"

# T09: Update Product
$resT09 = Invoke-Api -Method "PUT" -Path "/api/crm/products-services/$c1ProdId" -Token $c1AdminToken -Body @{
    Name = "$testProdName Updated"
    Code = "AI-ANL-UPD"
    Price = 37500.00
    IsActive = $true
}
$t09Pass = $resT09.Success -and ($resT09.Data.data.price -eq 37500.00)
Record-Test "PRD-05" "Admin Updates Product/Service Details" $t09Pass $resT09.DurationMs "New Price: 37500"

# T10: Soft Inactivation
$resT10 = Invoke-Api -Method "PATCH" -Path "/api/crm/products-services/$c1ProdId/status" -Token $c1AdminToken -Body @{
    IsActive = $false
}
$t10Pass = $resT10.Success -and ($resT10.Data.data.isActive -eq $false)
Record-Test "PRD-06" "Soft Inactivate Product/Service" $t10Pass $resT10.DurationMs "IsActive = false"

# T11: User Lead Creation DDL Excludes Inactive Records
$resT11 = Invoke-Api -Method "GET" -Path "/api/crm/products-services?activeOnly=true&search=$uniqueSuffix" -Token $c1UserToken
$inactiveVisible = $resT11.Data.data.items | Where-Object { $_.productServiceId -eq $c1ProdId }
$t11Pass = $resT11.Success -and ($inactiveVisible.Count -eq 0)
Record-Test "PRD-07" "Inactive Product Excluded from Lead Creation DDL" $t11Pass $resT11.DurationMs "Zero inactive returned"

# T12: Management Screen Shows Inactive with Filter
$resT12 = Invoke-Api -Method "GET" -Path "/api/crm/products-services?activeOnly=false&search=$uniqueSuffix" -Token $c1AdminToken
$inactiveFound = @($resT12.Data.data.items) | Where-Object { $_.productServiceId -eq $c1ProdId -and $_.isActive -eq $false }
Record-Test "PRD-08" "Management Screen Retains Inactive Records" (@($inactiveFound).Count -ge 1) $resT12.DurationMs "Found in All/Inactive view: $(@($inactiveFound).Count)"

# T13: Role Permission Check - User cannot create product
$resT13 = Invoke-Api -Method "POST" -Path "/api/crm/products-services" -Token $c1UserToken -Body @{
    Name = "Unauthorized Product by User"
}
$t13Pass = (!$resT13.Success) -and ($resT13.StatusCode -eq 403)
Record-Test "PRD-09" "Unauthorized User Cannot Create Product (403 Forbidden)" $t13Pass $resT13.DurationMs "Blocked by API authorization"

# T14: Role Permission Check - User cannot inactivate product
$resT14 = Invoke-Api -Method "PATCH" -Path "/api/crm/products-services/$c1ProdId/status" -Token $c1UserToken -Body @{
    IsActive = $true
}
$t14Pass = (!$resT14.Success) -and ($resT14.StatusCode -eq 403)
Record-Test "PRD-10" "Unauthorized User Cannot Inactivate Product (403 Forbidden)" $t14Pass $resT14.DurationMs "Blocked by API authorization"

# T15: Cross-Tenant Inactivation Blocked
$resT15 = Invoke-Api -Method "PATCH" -Path "/api/crm/products-services/$c1ProdId/status" -Token $c2AdminToken -Body @{
    IsActive = $false
}
$t15Pass = (!$resT15.Success) -and ($resT15.StatusCode -eq 400 -or $resT15.StatusCode -eq 404)
Record-Test "PRD-11" "Prevent Cross-Tenant Inactivation / Modification" $t15Pass $resT15.DurationMs "Blocked with $($resT15.StatusCode)"

# Re-activate for clean state
$null = Invoke-Api -Method "PATCH" -Path "/api/crm/products-services/$c1ProdId/status" -Token $c1AdminToken -Body @{ IsActive = $true }

Write-Host "`n=================================================================="
Write-Host " TEST EXECUTION SUMMARY"
Write-Host "=================================================================="
$passCount = ($tests | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = ($tests | Where-Object { $_.Status -eq "FAIL" }).Count
Write-Host "Total: $($tests.Count) | Passed: $passCount | Failed: $failCount`n"
$tests | Format-Table -AutoSize
