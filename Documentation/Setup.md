# LiveTracking + CRM System — Setup Guide

This project is a multi-tenant workforce-tracking system (GPS attendance/leave/shifts) with a
CRM module (Leads/Follow-ups/KPI/Productivity) built on top of it. Both share the same API,
database, and Android app.

## 1. Database

Target: SQL Server, database `LiveTrackingDB`. Run these scripts **in this order** (all are
idempotent — safe to re-run):

1. `Database/CreateDatabase.sql` — creates the `LiveTrackingDB` database if it doesn't exist.
2. `create_companies_and_sp.sql` (repo root) — the multi-tenant migration: creates
   `myonline_tbl_Companies`, retrofits `CompanyId` onto `Users`/`OfficeLocations`/`Shifts`/
   `LeaveTypes`/`AppVersions`, and creates the company-quota stored procedures
   (`sp_GetCompanySummary`, `sp_ValidateCompanyUserQuota`, `sp_GetCompanyBranchesAndOfficers`).
3. `Database/CreateCrmTablesAndSeed.sql` — creates the 7 CRM tables (`myonline_tbl_CRM_*`:
   ProductServices, LeadSources, Leads, LeadAssignments, LeadFollowUps, LeadRemarks, KPI) and
   seeds default lead sources/products/KPI targets for every active company.
4. `Database/CreateCrmStatusHistoryTable.sql` and `Database/CreateCrmAuditLogTable.sql` — CRM
   lead status-history and audit-log tables.
5. `Database/CreateIndexes.sql` / `Database/CreateStoredProcedures.sql` / `Database/SeedData.sql`
   — **do not use these.** They target an older, pre-multi-tenant schema (plain `dbo.Users`,
   no `CompanyId`, no `myonline_tbl_` prefix) and are incompatible with the current
   `LiveTrackingDbContext` model. They are kept in the repo for historical reference only.

Actual runtime table names all use the `myonline_tbl_` prefix (e.g. `myonline_tbl_Users`,
`myonline_tbl_CRM_Leads`) — this is enforced by Fluent API mappings in
`LiveTracking.Api/Data/LiveTrackingDbContext.cs`, not by the raw table names in `CreateTables.sql`.

`Database/truncate_and_reset_all_data.sql` performs a full destructive reset + reseeds two demo
companies (MOXX, Beta Tech Solutions) with sample users/offices/leads — useful for a clean test
environment, but **irreversible**; do not run against real data.

## 2. API (LiveTracking.Api)

1. Requires .NET 8 SDK.
2. Configure secrets via environment variables or `dotnet user-secrets` — do **not** hardcode
   real credentials in `appsettings.json`/`appsettings.Production.json`:
   - `ConnectionStrings__DefaultConnection` — SQL Server connection string for `LiveTrackingDB`.
   - `Jwt__Key` — a long random secret (32+ chars), `Jwt__Issuer`, `Jwt__Audience`, `Jwt__ExpiryHours`.
3. From `LiveTracking.Api/`, run:
   ```
   dotnet restore
   dotnet run
   ```
4. Swagger UI is available at `/swagger` (currently enabled in all environments, including when
   `ASPNETCORE_ENVIRONMENT=Production` — be aware this exposes the API surface publicly at that
   path unless restricted at the network/reverse-proxy level).
5. Docker: from the repo root, `docker compose up -d --build` builds `LiveTracking.Api/Dockerfile`
   and starts the `livetracking-api` service on port `8080`. The compose file expects SQL Server
   to be reachable from the container via `host.docker.internal` (i.e., running on the Docker
   host, not containerized) — set `SA_PASSWORD`/connection env vars in `docker-compose.yml` or an
   `.env` file rather than committing real values.

### Key endpoint groups
- `POST /api/auth/login` — returns JWT + role (`Admin` / `Manager` / `User`) + companyId.
  Enforces single-device binding for **every** role (first login binds the device; login from a
  different device is rejected until an Admin re-enables the account).
- `GET/POST/PUT /api/users` — Admin only, create/edit/disable employees, Managers, and sub-admins.
- `POST /api/locations/ping`, `GET /api/locations/latest`, SignalR hub `/hubs/location` — GPS
  tracking (JWT passed as `?access_token=` for the websocket handshake).
- `api/crm/manager/*` — CRM management endpoints (`Admin` or `Manager` role): dashboard, leads,
  assignment, follow-ups, KPI targets, productivity, product/service and lead-source master data,
  CSV export.
- `api/crm/user/*` — CRM employee self-service endpoints (any authenticated role): personal
  dashboard, leads, status updates, follow-ups, remarks, KPI performance.

Tenant isolation is enforced **in application code**, not via EF Core global query filters —
every CRM query filters explicitly by the caller's `companyId` (from the JWT claim, with a DB
fallback if absent). There is no automatic ORM-level tenant guard, so any new query added to
`CrmService.cs` (or elsewhere) must include an explicit `CompanyId` filter.

## 3. Android App (CRM_Apps)

1. Open the `CRM_Apps` folder (not `AndroidApp` — that name is stale from an earlier iteration
   of this project) as an Android Studio project. Package: `com.zynexbd.crmsolution`, min SDK 26,
   target SDK 34, Kotlin, XML views + ViewBinding (no Compose).
2. Set the API base URL in `app/build.gradle.kts` under `defaultConfig.buildConfigField`:
   - `API_BASE_URL` — currently hardcoded to `http://127.0.0.1:8080/` for local development; there
     is no separate build variant for staging/production yet, so change this per-build before
     testing against a non-local API.
   - `SIGNALR_HUB_URL` — matching base URL + `/hubs/location`.
3. Set the Google Maps API key in `local.properties` (`MAPS_API_KEY=...`, gitignored).
4. Build and run on a device/emulator with Google Play Services.

### Login / role-based navigation flow
1. `LoginActivity` posts credentials + `deviceId`/`deviceModel` to `POST /api/auth/login`.
2. On success, the JWT, role, and user info are saved via `SessionManager` (plain
   `SharedPreferences` — not encrypted; treat this as a known hardening item, not something to
   rely on for defense-in-depth).
3. Routing by `role`: `Admin` and `Manager` → `AdminCrmDashboardActivity` (CRM-first landing
   screen); `User` → `UserHomeActivity` (attendance/tracking + CRM quick links), which also
   starts `TrackingForegroundService`.
4. The original LiveTracking admin screens (live map, route history, attendance, leave, shifts,
   holidays, offices) still exist and are manifest-registered, but are hidden from the Admin
   drawer menu in favor of the CRM-first experience — they are not deleted, just de-surfaced.
5. `BootReceiver` restarts `TrackingForegroundService` after device boot if the last session was
   a logged-in field User.

## Assumptions / known gaps

- EF Core (SQL Server provider) is used in the API; a handful of legacy stored procedures
  (subscription/attendance summaries) are still called via raw ADO for reporting.
- Password hashing uses ASP.NET Core Identity's `PasswordHasher<User>`.
- CRM has no office-location-level scoping today — Admins and Managers of the same company see
  the same company-wide lead pool regardless of `OfficeLocationId`. Adding office-scoping is a
  larger follow-up (new JWT claim, query filters, Android office-switcher UI).
- The separate legacy "Customer/Visit" module (`CustomerListActivity`, `RecordVisitActivity`,
  `api/customers`/`api/visits`) still exists alongside the newer Lead-based CRM and is
  intentionally left untouched — the two are not yet consolidated.
