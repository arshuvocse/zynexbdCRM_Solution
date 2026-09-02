# CRM Solution & Live Tracking System
## Enterprise Multi-Tenant Overhaul & Full Project Implementation Details
**Language: English & বাংলা (Bilingual Documentation)**  
**Generated Date:** September 2026  
**Status:** Production Ready  

---

## ১. ভূমিকা ও প্রজেক্ট সারসংক্ষেপ (Executive Summary)

**CRM Solution & Live Tracking** হলো একটি উচ্চক্ষমতাসম্পন্ন, মাল্টি-টেন্যান্ট (Multi-Tenant) এন্টারপ্রাইজ সিস্টেম। এতে রয়েছে:
1. **Android Mobile Application (`CRM_Apps`)**: আধুনিক Material Design 3, Kotlin, Coroutines, MVVM আর্কিটেকচার এবং কাস্টম ক্যানভাস চার্ট।
2. **Backend Web API (`LiveTracking.Api`)**: ASP.NET Core 8.0, Entity Framework Core, Dapper/ADO.NET Stored Procedure integration, JWT Auth, SignalR রিয়েল-টাইম ট্র্যাকিং এবং ব্যাকগ্রাউন্ড হোস্টেড সার্ভিসেস।
3. **Database Architecture (`crm_solution_DB`)**: মাইক্রোসফট এসকিউএল সার্ভার (MSSQL), যাতে রয়েছে ২৫টি রিলেশনাল টেবিল, কম্পোজিট ইনডেক্স এবং ৬টি অপ্টিমাইজড এন্টারপ্রাইজ স্টোরড প্রসিডিউর।

---

## ২. ডেটাবেস টিয়ার (Database Tier & Optimization)

### ২.১ ডেটাবেস স্কিমা ও আইসোলেশন
- **`crm_solution_DB` এক্টিভেশন**: মোট ২৫টি টেবিল নিয়ে সম্পূর্ণ সিআরএম ডাটাবেজ স্ট্রাকচার প্রস্তুত করা হয়েছে (`Database/crmdbscript.sql`)।
- **`LiveTrackingDB` রোলব্যাক**: পূর্বে `LiveTrackingDB`-তে থাকা CRM সংক্রান্ত স্টোরড প্রসিডিউর ও ইনডেক্স ক্লিন করে সম্পূর্ণ আলাদা `crm_solution_DB`-তে কেন্দ্রীভূত করা হয়েছে।
- **মাল্টি-টেন্যান্ট ডেটা নিরাপত্তা**: প্রতিটি কুয়েরি ও স্টোরড প্রসিডিউরে বাধ্যতামূলকভাবে `CompanyId` ফিল্টারিং কার্যকর রাখা হয়েছে যাতে এক কোম্পানির ডেটা অন্য কোম্পানি কোনোভাবেই দেখতে না পারে।

### ২.২ কম্পোজিট পারফরম্যান্স ইনডেক্স (Composite Indexes)
কুয়েরি এক্সিকিউশন টাইম মিলিসেকেন্ডে নামিয়ে আনতে ২টি ক্রিটিক্যাল কম্পোজিট ইনডেক্স যুক্ত করা হয়েছে:
1. `IX_CRM_Leads_Company_Office_Status_Dates`:
   - কভারিং কলাম: `CompanyId, IsActive, LeadStatus, AssignedToUserId, OfficeLocationId, CreatedAt`
   - উদ্দেশ্য: ড্যাশবোর্ড ও রিপোর্ট ফিল্টারিংয়ে টেবিল স্ক্যান বন্ধ করে উচ্চগতির ইনডেক্স সিক নিশ্চিত করা।
2. `IX_CRM_LeadFollowUps_Company_Dates_Status`:
   - কভারিং কলাম: `CompanyId, FollowUpDate, FollowUpStatus, LeadId, AssignedToUserId`
   - উদ্দেশ্য: আজকের ফলো-আপ, বকেয়া (Overdue) এবং আপকামিং ফলো-আপ কুয়েরি দ্রুত সম্পন্ন করা।

### ২.৩ এন্টারপ্রাইজ স্টোরড প্রসিডিউরসমূহ (Stored Procedures)
শূন্য দিয়ে ভাগ হওয়া রোধে (`NULLIF`, `CASE WHEN > 0`) এবং সাব-সেকেন্ড (Sub-second) রেসপন্স টাইম নিশ্চিত করতে ৬টি স্টোরড প্রসিডিউর তৈরি ও টিউন করা হয়েছে:

| Stored Procedure | Scope / Role | বর্ণনা ও রেজাল্ট সেট |
| :--- | :--- | :--- |
| `dbo.sp_Crm_GetAdminDashboard` | Admin | **৯টি রেজাল্ট সেট**: ১১টি সামারি মেট্রিক কার্ড (Total Leads, New, Overdue, Conversion Rate, Won Value ইত্যাদি) + ৮টি চার্ট ডেটাসেট। |
| `dbo.sp_Crm_GetManagerDashboard` | Manager | **৯টি রেজাল্ট সেট**: ৯টি টিম মেট্রিক কার্ড + ৮টি চার্ট ডেটাসেট (অফিস লোকেশন ও টিম মেম্বারদের পারফরম্যান্স)। |
| `dbo.sp_Crm_GetUserDashboard` | User / Sales Exec | **৬টি রেজাল্ট সেট**: ১০টি ব্যক্তিগত মেট্রিক কার্ড + ৫টি ভিজ্যুয়াল চার্ট ও দৈনিক/সাপ্তাহিক/মাসিক KPI অগ্রগতি। |
| `dbo.sp_Crm_GetAdminReports` | Admin | **২টি রেজাল্ট সেট**: ১৫টি ভিন্ন কোম্পানির রিপোর্টের ডায়নামিক সামারি ও পেজিনেটেড ডেটা। |
| `dbo.sp_Crm_GetManagerReports` | Manager | **২টি রেজাল্ট সেট**: ১৩টি ভিন্ন টিম পারফরম্যান্স ও কনভার্সন রিপোর্টের ডায়নামিক সামারি ও পেজিনেটেড ডেটা। |
| `dbo.sp_Crm_GetUserReports` | User / Sales Exec | **২টি রেজাল্ট সেট**: ১৩টি ব্যক্তিগত সেলস ও ফলো-আপ রিপোর্টের ডায়নামিক সামারি ও পেজিনেটেড ডেটা। |

---

## ৩. ব্যাকএন্ড এপিআই টিয়ার (Backend Web API - `LiveTracking.Api`)

### ৩.১ সিকিউরিটি ও মাল্টি-টেন্যান্সি (Tenant Security)
- **`CompaniesController.cs` ফিক্স**: নন-সুপারএডমিন যাতে অন্য কোম্পানির ডেটা অ্যাক্সেস করতে না পারে, সেজন্য কন্ট্রোলারের সমস্ত অ্যাকশনে `GetCurrentCompanyIdAsync()` বাধ্যতামুলক করা হয়েছে।
- **কঠোর রোল সেপারেশন (RBAC)**:
  - Admin: `[Authorize(Roles = "Admin")]`
  - Manager: `[Authorize(Roles = "Manager,Admin")]` (অফিস লোকেশন ভ্যালিডেশন সহ)
  - User: `[Authorize(Roles = "User,Employee,Manager,Admin")]` (কেবলমাত্র নিজস্ব লিড ও ফলো-আপ অ্যাক্সেস)

### ৩.২ নতুন ও আপগ্রেডেড কন্ট্রোলারসমূহ
1. **`CrmAdminController.cs` (নতুন)**:
   - `GET /api/crm/admin/dashboard`
   - `GET /api/crm/admin/reports` (১৫টি এন্টারপ্রাইজ রিপোর্ট)
   - `GET /api/crm/admin/reports/export` (স্ট্রিমিং CSV এক্সপোর্ট)
2. **`CrmManagerController.cs` (আপগ্রেডেড)**:
   - `GET /api/crm/manager/dashboard/analytics` (টিম ভিত্তিক ৯টি কার্ড ও ৮টি চার্ট)
   - `GET /api/crm/manager/reports` (১৩টি টিম রিপোর্ট)
   - `GET /api/crm/manager/reports/export`
3. **`CrmUserController.cs` (আপগ্রেডেড)**:
   - `GET /api/crm/user/dashboard/analytics` (ব্যক্তিগত ১০টি কার্ড ও ৫টি চার্ট)
   - `GET /api/crm/user/reports` (১৩টি ব্যক্তিগত রিপোর্ট)
   - `GET /api/crm/user/followups` (স্মার্ট ফিল্টার: `today`, `tomorrow`, `overdue`, `upcoming`, `next7days`, `next15days`, `next30days`, `custom`)

### ৩.৩ সার্ভিস লেয়ার ও হোস্ট সার্ভিসেস
- **`CrmService.cs`**: ADO.NET মাল্টি-রেজাল্ট রিডার দিয়ে ডেটাবেসের একাধিক রেজাল্ট সেট প্রসেস করা।
- **হোস্টেড ব্যাকগ্রাউন্ড সার্ভিসসমূহ**:
  - `AttendanceShiftHostedService`: স্বয়ংক্রিয় শিফট চেকিং ও অ্যাটেন্ডেন্স প্রসেসিং।
  - `CrmFollowUpReminderHostedService`: নির্ধারিত ফলো-আপের স্বয়ংক্রিয় নোটিফিকেশন সিস্টেম।
  - `SubscriptionReminderHostedService`: সাবস্ক্রিপশন মেয়াদোত্তীর্ণ হওয়ার নোটিফিকেশন।

---

## ৪. অ্যান্ড্রয়েড মোবাইল অ্যাপ্লিকেশান (`CRM_Apps`)

### ৪.১ কাস্টম চার্ট কম্পোনেন্ট (Native Canvas Charts)
তৃতীয় পক্ষের ভারী লাইব্রেরি ছাড়া অ্যান্ড্রয়েডের নিজস্ব ক্যানভাস ব্যবহার করে দ্রুতগতির ইন্টার‍্যাক্টিভ চার্ট তৈরি করা হয়েছে:
1. `FunnelChartView.kt`: সেলস পাইপলাইনের প্রতিটি স্টেজের কনভার্সন ড্রপ-অফ ও প্রগ্রেস বার এনিমেশন।
2. `DonutChartView.kt`: লিড স্ট্যাটাস ও ডিস্ট্রিবিউশন পাই/ডোনাট চার্ট (মাঝখানে মোট কাউন্ট ও নিচে কালার লিজেন্ড সহ)।
3. `BarChartView.kt`: মাসিক ও কর্মীভিত্তিক তুলনা করার জন্য ডুয়াল-বার চার্ট।

### ৪.২ ড্যাশবোর্ড স্ক্রিনসমূহ
- **`AdminCrmDashboardActivity.kt`**:
  - ১১টি সামারি মেট্রিক কার্ড।
  - ৮টি চার্ট (Funnel, Donut, Trend, Employee Ranking, Product Breakdown, Source Analysis ইত্যাদি)।
  - ডেট ফিল্টার (Today, This Week, This Month, All Time)।
  - অ্যাডমিন ড্রয়ার মেনু ও কুইক একশন।
- **`ManagerCrmDashboardActivity.kt` (নতুন)**:
  - ম্যানেজারদের জন্য সম্পূর্ণ আলাদা ড্যাশবোর্ড স্ক্রিন।
  - ৯টি মেট্রিক কার্ড ও ৮টি টিম-লেভেল অ্যানালিটিক্স চার্ট।
- **`UserCrmDashboardActivity.kt`**:
  - ১০টি ব্যক্তিগত পারফরম্যান্স কার্ড ও ৫টি চার্ট।
  - দৈনিক, সাপ্তাহিক ও মাসিক KPI লক্ষ্যমাত্রা ও অর্জনের প্রগ্রেস বার।

### ৪.৩ রিপোর্টিং ও এক্সপোর্ট মডিউল
- **`AdminCrmReportsActivity.kt` (নতুন)**: ১৫টি অ্যাডমিন রিপোর্ট, রিয়েল-টাইম ফিল্টারিং, সামারি হেডার কার্ড এবং সরাসরি ডিভাইসে CSV এক্সপোর্ট (`FileProvider` এর মাধ্যমে)।
- **`ManagerCrmReportsActivity.kt` (নতুন)**: ১৩টি টিম রিপোর্ট ও টিম মেম্বার সিলেক্টর।
- **`UserCrmReportsActivity.kt` (নতুন)**: ১৩টি ব্যক্তিগত রিপোর্ট।

### ৪.৪ রাউটিং ও ন্যাভিগেশন ফিক্স
- **`LoginActivity.kt`**:
  - `Admin` লগইন করলে যাবে `AdminCrmDashboardActivity`-তে।
  - `Manager` লগইন করলে যাবে `ManagerCrmDashboardActivity`-তে।
  - `User` লগইন করলে যাবে `UserHomeActivity`-তে।
- **`UserHomeActivity.kt`**:
  - "Follow Ups" বাটনে ক্লিক করলে সঠিকভাবে আপগ্রেডেড `UserCrmFollowUpsActivity` ওপেন হবে।
- **`RecordVisitActivity.kt`**:
  - কাস্টমার সার্চের জন্য Select2 স্টাইলের সার্চেবল ডায়ালগ (`dialog_select2_customer.xml`, `SelectCustomerAdapter.kt`) ইন্টিগ্রেশন।
- অ্যাপের ব্র্যান্ডিং লোগো এবং অ্যাডাপ্টিভ আইকন আপডেট সম্পন্ন।

---

## ৫. ফাইল পরিবর্তনের সম্পূর্ণ তালিকা (File Matrix)

| ক্যাটাগরি | ফাইল পাথ | পরিবর্তনের বিবরণ |
| :--- | :--- | :--- |
| **Database** | `Database/crmdbscript.sql` | ২৫টি টেবিল, কম্পোজিট ইনডেক্স ও ৬টি অপ্টিমাইজড এসপি। |
| **Database** | `Database/UnifiedProductionCrmMigration.sql` | প্রোডাকশন মাইগ্রেশন ও এসপি ডিপ্লয়মেন্ট স্ক্রিপ্ট। |
| **Backend** | `LiveTracking.Api/Controllers/CrmAdminController.cs` | **[NEW]** অ্যাডমিন ড্যাশবোর্ড, ১৫টি রিপোর্ট ও CSV এক্সপোর্ট। |
| **Backend** | `LiveTracking.Api/Controllers/CrmManagerController.cs` | ম্যানেজার ড্যাশবোর্ড, ১৩টি রিপোর্ট ও এক্সপোর্ট এপিআই। |
| **Backend** | `LiveTracking.Api/Controllers/CrmUserController.cs` | ইউজার ড্যাশবোর্ড, ১৩টি রিপোর্ট ও ৮টি ফলো-আপ ফিল্টার। |
| **Backend** | `LiveTracking.Api/Controllers/CompaniesController.cs` | ক্রস-টেন্যান্ট ডেটা লিক প্রতিরোধ ফিক্স। |
| **Backend** | `LiveTracking.Api/Services/CrmService.cs` | মাল্টি-রেজাল্ট স্টোরড প্রসিডিউর এক্সেকিউশন ইঞ্জিন। |
| **Backend** | `LiveTracking.Api/DTOs/CrmDtos.cs` | ড্যাশবোর্ড, চার্ট ও রিপোর্টের জন্য প্রয়োজনীয় DTO সমূহ। |
| **Android** | `CRM_Apps/.../views/FunnelChartView.kt` | **[NEW]** সেলস ফানেল কাস্টম ক্যানভাস ভিউ। |
| **Android** | `CRM_Apps/.../views/DonutChartView.kt` | ডোনাট স্ট্যাটাস ডিস্ট্রিবিউশন চার্ট ভিউ। |
| **Android** | `CRM_Apps/.../views/BarChartView.kt` | পারফরম্যান্স বার চার্ট ভিউ। |
| **Android** | `CRM_Apps/.../activities/ManagerCrmDashboardActivity.kt` | **[NEW]** ডেডিকেটেড ম্যানেজার ড্যাশবোর্ড এক্টিভিটি। |
| **Android** | `CRM_Apps/.../activities/AdminCrmDashboardActivity.kt` | ১১টি কার্ড ও ৮টি চার্ট সহ অ্যাডমিন ড্যাশবোর্ড। |
| **Android** | `CRM_Apps/.../activities/UserCrmDashboardActivity.kt` | ১০টি কার্ড ও ৫টি চার্ট সহ ইউজার ড্যাশবোর্ড। |
| **Android** | `CRM_Apps/.../activities/AdminCrmReportsActivity.kt` | **[NEW]** ১৫টি অ্যাডমিন রিপোর্ট স্ক্রিন ও CSV এক্সপোর্ট। |
| **Android** | `CRM_Apps/.../activities/ManagerCrmReportsActivity.kt` | **[NEW]** ১৩টি ম্যানেজার টিম রিপোর্ট স্ক্রিন। |
| **Android** | `CRM_Apps/.../activities/UserCrmReportsActivity.kt` | **[NEW]** ১৩টি ইউজার রিপোর্ট স্ক্রিন। |
| **Android** | `CRM_Apps/.../adapters/SelectCustomerAdapter.kt` | **[NEW]** কাস্টমার সার্চ ও ফিল্টারিং এডাপ্টার। |
| **Android** | `CRM_Apps/.../activities/LoginActivity.kt` | রোল ভিত্তিক স্বয়ংক্রিয় ড্যাশবোর্ড রিডাইরেকশন। |
| **Android** | `CRM_Apps/.../activities/UserHomeActivity.kt` | ফলো-আপ নেভিগেশন রাউটিং সমাধান। |
| **Android** | `CRM_Apps/.../AndroidManifest.xml` | সমস্ত নতুন এক্টিভিটি ও ফাইল প্রোভাইডার রেজিস্ট্রেশন। |

---

## ৬. ভেরিফিকেশন ও কোয়ালিটি অডিট ফলাফল (Validation Results)

1. **Backend .NET Build**:
   ```powershell
   dotnet build LiveTracking.Api/LiveTracking.Api.csproj
   # Output: Build succeeded. 0 Warning(s), 0 Error(s).
   ```
2. **Android Gradle Build**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   .\gradlew.bat assembleDebug
   # Output: BUILD SUCCESSFUL (Debug APK তৈরি সম্পন্ন)
   ```
3. **Database Stored Procedures & Multi-Tenant QA Test**:
   - `dbo.sp_Crm_GetAdminDashboard`: ৯টি রেজাল্ট সেট সফলভাবে রিটার্ন করেছে।
   - `dbo.sp_Crm_GetManagerDashboard`: ৯টি রেজাল্ট সেট সফলভাবে রিটার্ন করেছে।
   - `dbo.sp_Crm_GetUserDashboard`: ৬টি রেজাল্ট সেট সফলভাবে রিটার্ন করেছে।
   - Admin Reports (১ থেকে ১৫): সবগুলো রিপোর্ট সফলভাবে এক্সিকিউট হয়েছে।
   - Manager Reports (১ থেকে ১৩): সবগুলো রিপোর্ট সফলভাবে এক্সিকিউট হয়েছে।
   - User Reports (১ থেকে ১৩): সবগুলো রিপোর্ট সফলভাবে এক্সিকিউট হয়েছে।
   - **Cross-Tenant Security Test**: অন্য টেন্যান্ট আইডি দিয়ে টেস্ট করার পর ০ রিটার্ন করেছে, কোনো ডেটা লিক নেই।

---

## ৭. উপসংহার (Conclusion)
প্রজেক্টের ডেটাবেস, ব্যাকএন্ড এপিআই এবং অ্যান্ড্রয়েড মোবাইল ক্লায়েন্ট—তিনটি টিয়ারই সম্পূর্ণ সিঙ্ক্রোনাইজড, সিকিউর এবং প্রোডাকশন-রেডি অবস্থায় রয়েছে।
