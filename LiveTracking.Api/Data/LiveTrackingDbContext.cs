using LiveTracking.Api.Models;
using LiveTracking.Api.Models.CRM;
using Microsoft.EntityFrameworkCore;

namespace LiveTracking.Api.Data;

public class LiveTrackingDbContext : DbContext
{
    public LiveTrackingDbContext(DbContextOptions<LiveTrackingDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<DriverLocation> DriverLocations => Set<DriverLocation>();
    public DbSet<AttendanceRecord> AttendanceRecords => Set<AttendanceRecord>();
    public DbSet<LeaveType> LeaveTypes => Set<LeaveType>();
    public DbSet<LeaveBalance> LeaveBalances => Set<LeaveBalance>();
    public DbSet<LeaveApplication> LeaveApplications => Set<LeaveApplication>();
    public DbSet<OfficeLocation> OfficeLocations => Set<OfficeLocation>();
    public DbSet<Customer> Customers => Set<Customer>();
    public DbSet<CustomerVisit> CustomerVisits => Set<CustomerVisit>();
    public DbSet<Shift> Shifts => Set<Shift>();
    public DbSet<Company> Companies => Set<Company>();
    public DbSet<Holiday> Holidays => Set<Holiday>();
    public DbSet<AppVersion> AppVersions => Set<AppVersion>();
    public DbSet<NotificationItem> Notifications => Set<NotificationItem>();
    public DbSet<SubscriptionPlan> SubscriptionPlans => Set<SubscriptionPlan>();
    public DbSet<AdminOfficeLocation> AdminOfficeLocations => Set<AdminOfficeLocation>();

    // CRM DbSets
    public DbSet<CrmProductService> CrmProductServices => Set<CrmProductService>();
    public DbSet<CrmLeadSource> CrmLeadSources => Set<CrmLeadSource>();
    public DbSet<CrmLead> CrmLeads => Set<CrmLead>();
    public DbSet<CrmLeadAssignment> CrmLeadAssignments => Set<CrmLeadAssignment>();
    public DbSet<CrmLeadFollowUp> CrmLeadFollowUps => Set<CrmLeadFollowUp>();
    public DbSet<CrmLeadRemark> CrmLeadRemarks => Set<CrmLeadRemark>();
    public DbSet<CrmKpi> CrmKpis => Set<CrmKpi>();
    public DbSet<CrmLeadStatusHistory> CrmLeadStatusHistories => Set<CrmLeadStatusHistory>();
    public DbSet<CrmAuditLog> CrmAuditLogs => Set<CrmAuditLog>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Company>(e =>
        {
            e.ToTable("myonline_tbl_Companies");
            e.HasKey(c => c.CompanyId);
            e.HasIndex(c => c.CompanyCode).IsUnique();
            e.Property(c => c.CompanyName).HasMaxLength(200);
            e.Property(c => c.CompanyCode).HasMaxLength(50);
            e.HasMany(c => c.Users)
                .WithOne(u => u.Company)
                .HasForeignKey(u => u.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);
            e.HasMany(c => c.OfficeLocations)
                .WithOne(o => o.Company)
                .HasForeignKey(o => o.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);
        });
        modelBuilder.Entity<Holiday>(e =>
        {
            e.ToTable("myonline_tbl_Holidays");
            e.HasKey(h => h.HolidayId);
            e.Property(h => h.Name).HasMaxLength(150);
        });

        modelBuilder.Entity<Shift>(e =>
        {
            e.ToTable("myonline_tbl_Shifts");
            e.HasKey(s => s.ShiftId);
            e.Property(s => s.ShiftName).HasMaxLength(100);
        });

        modelBuilder.Entity<Customer>(e =>
        {
            e.ToTable("myonline_tbl_Customers");
            e.HasKey(c => c.CustomerId);
            e.HasIndex(c => new { c.Name, c.Mobile });
        });

        modelBuilder.Entity<CustomerVisit>(e =>
        {
            e.ToTable("myonline_tbl_CustomerVisits");
            e.HasKey(v => v.VisitId);
            e.HasOne(v => v.Customer)
                .WithMany(c => c.Visits)
                .HasForeignKey(v => v.CustomerId);
            e.HasOne(v => v.User)
                .WithMany()
                .HasForeignKey(v => v.UserId);
        });
        modelBuilder.Entity<User>(e =>
        {
            e.ToTable("myonline_tbl_Users");
            e.HasKey(u => u.UserId);
            e.Property(u => u.UserId).HasColumnName("Id");
            e.Property(u => u.FullName).HasColumnName("Name");
            e.Property(u => u.CreatedAtUtc).HasColumnName("CreatedAt");
            e.Ignore(u => u.UpdatedAtUtc);
            e.HasIndex(u => u.Username).IsUnique();
            e.Property(u => u.Role).HasMaxLength(20);
            e.Property(u => u.CreatedByAdminId).HasColumnName("CreatedByAdminId");
            e.HasOne(u => u.OfficeLocation)
                .WithMany()
                .HasForeignKey(u => u.OfficeLocationId);
            e.HasOne(u => u.Shift)
                .WithMany()
                .HasForeignKey(u => u.ShiftId);
            e.HasMany(u => u.AdminOfficeLocations)
                .WithOne(a => a.AdminUser)
                .HasForeignKey(a => a.AdminUserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<DriverLocation>(e =>
        {
            e.ToTable("myonline_tbl_DriverLocations");
            e.HasKey(l => l.LocationId);
            e.Property(l => l.LocationId).HasColumnName("Id");
            e.Property(l => l.RecordedAtUtc).HasColumnName("RecordedAt");
            e.Property(l => l.LocationAddress).HasColumnName("LocationAddress").HasMaxLength(500);
            e.Ignore(l => l.ReceivedAtUtc);
            e.HasIndex(l => new { l.UserId, l.RecordedAtUtc });
            e.HasOne(l => l.User)
                .WithMany(u => u.Locations)
                .HasForeignKey(l => l.UserId);
        });

        modelBuilder.Entity<AttendanceRecord>(e =>
        {
            e.ToTable("myonline_tbl_Attendances");
            e.HasKey(a => a.AttendanceId);
            e.Property(a => a.AttendanceId).HasColumnName("Id");
            e.Property(a => a.RecordedAtUtc).HasColumnName("Timestamp");
            e.Property(a => a.SelfieUrl).HasColumnName("SelfieImagePath");
            e.HasOne(a => a.User)
                .WithMany()
                .HasForeignKey(a => a.UserId);
        });

        modelBuilder.Entity<LeaveType>(e =>
        {
            e.ToTable("myonline_tbl_LeaveTypes");
            e.HasKey(t => t.LeaveTypeId);
            e.Property(t => t.LeaveTypeId).HasColumnName("Id");
        });

        modelBuilder.Entity<LeaveBalance>(e =>
        {
            e.ToTable("myonline_tbl_LeaveBalances");
            e.HasKey(b => b.LeaveBalanceId);
            e.Property(b => b.LeaveBalanceId).HasColumnName("Id");
            e.Property(b => b.Year).HasColumnName("Year");
            e.HasOne(b => b.User)
                .WithMany()
                .HasForeignKey(b => b.UserId);
            e.HasOne(b => b.LeaveType)
                .WithMany()
                .HasForeignKey(b => b.LeaveTypeId);
        });

        modelBuilder.Entity<LeaveApplication>(e =>
        {
            e.ToTable("myonline_tbl_LeaveApplications");
            e.HasKey(a => a.LeaveApplicationId);
            e.Property(a => a.LeaveApplicationId).HasColumnName("Id");
            e.Property(a => a.AppliedAtUtc).HasColumnName("AppliedAt");
            e.Property(a => a.ReviewedAtUtc).HasColumnName("ReviewedAt");
            e.Property(a => a.ReviewedBy).HasColumnName("ReviewedBy");
            e.HasOne(a => a.User)
                .WithMany()
                .HasForeignKey(a => a.UserId);
            e.HasOne(a => a.LeaveType)
                .WithMany()
                .HasForeignKey(a => a.LeaveTypeId);
        });

        modelBuilder.Entity<OfficeLocation>(e =>
        {
            e.ToTable("myonline_tbl_OfficeLocations");
            e.HasKey(o => o.OfficeLocationId);
            e.Property(o => o.OfficeLocationId).HasColumnName("Id");
            e.Property(o => o.CreatedAtUtc).HasColumnName("CreatedAt");
            e.HasMany(o => o.AdminOfficeLocations)
                .WithOne(a => a.OfficeLocation)
                .HasForeignKey(a => a.OfficeLocationId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<AdminOfficeLocation>(e =>
        {
            e.ToTable("myonline_tbl_AdminOfficeLocations");
            e.HasKey(a => a.Id);
            e.Property(a => a.Id).HasColumnName("Id");
            e.Property(a => a.AssignedAtUtc).HasColumnName("AssignedAtUtc");
            e.HasIndex(a => new { a.AdminUserId, a.OfficeLocationId }).IsUnique();
        });

        modelBuilder.Entity<AppVersion>(e =>
        {
            e.ToTable("myonline_tbl_AppVersions");
            e.HasKey(v => v.AppVersionId);
            e.Property(v => v.AppVersionId).HasColumnName("Id");
            e.Property(v => v.CreatedAtUtc).HasColumnName("CreatedAt");
            e.HasIndex(v => new { v.Platform, v.IsActive, v.VersionCode });
        });

        modelBuilder.Entity<NotificationItem>(e =>
        {
            e.ToTable("myonline_tbl_Notifications");
            e.HasKey(n => n.NotificationId);
            e.Property(n => n.NotificationId).HasColumnName("Id");
            e.Property(n => n.CreatedAtUtc).HasColumnName("CreatedAt");
            e.HasIndex(n => new { n.UserId, n.IsRead, n.CreatedAtUtc });
            e.HasOne(n => n.User)
                .WithMany()
                .HasForeignKey(n => n.UserId)
                .OnDelete(DeleteBehavior.SetNull);
            e.HasOne(n => n.Company)
                .WithMany()
                .HasForeignKey(n => n.CompanyId)
                .OnDelete(DeleteBehavior.Cascade);
        });


        // Seed Default Leave Types
        modelBuilder.Entity<LeaveType>().HasData(
            new LeaveType { LeaveTypeId = 1, Name = "Casual Leave", DefaultDaysPerYear = 14, IsActive = true },
            new LeaveType { LeaveTypeId = 2, Name = "Sick Leave", DefaultDaysPerYear = 14, IsActive = true },
            new LeaveType { LeaveTypeId = 3, Name = "Earned Leave", DefaultDaysPerYear = 10, IsActive = true }
        );

        // Seed Default Office Location
        modelBuilder.Entity<OfficeLocation>().HasData(
            new OfficeLocation { OfficeLocationId = 1, Name = "Headquarters", Latitude = 23.8103, Longitude = 90.4125, RadiusMeters = 200, IsActive = true }
        );

        // CRM Entity Mappings
        modelBuilder.Entity<CrmProductService>(e =>
        {
            e.ToTable("myonline_tbl_CRM_ProductServices");
            e.HasKey(p => p.ProductServiceId);
            e.Property(p => p.Name).HasMaxLength(150);
            e.Property(p => p.Code).HasMaxLength(50);
            e.Property(p => p.Description).HasMaxLength(500);
            e.Property(p => p.Price).HasPrecision(18, 2);
            e.HasOne(p => p.Company)
                .WithMany()
                .HasForeignKey(p => p.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<CrmLeadSource>(e =>
        {
            e.ToTable("myonline_tbl_CRM_LeadSources");
            e.HasKey(s => s.LeadSourceId);
            e.Property(s => s.Name).HasMaxLength(100);
            e.HasOne(s => s.Company)
                .WithMany()
                .HasForeignKey(s => s.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<CrmLead>(e =>
        {
            e.ToTable("myonline_tbl_CRM_Leads");
            e.HasKey(l => l.LeadId);
            e.Property(l => l.LeadName).HasMaxLength(200);
            e.Property(l => l.ContactPerson).HasMaxLength(150);
            e.Property(l => l.Phone).HasMaxLength(30);
            e.Property(l => l.Email).HasMaxLength(150);
            e.Property(l => l.Address).HasMaxLength(500);
            e.Property(l => l.LeadSourceType).HasMaxLength(50);
            e.Property(l => l.LeadStatus).HasMaxLength(50);
            e.Property(l => l.EstimatedValue).HasPrecision(18, 2);

            e.HasOne(l => l.Company)
                .WithMany()
                .HasForeignKey(l => l.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(l => l.ProductService)
                .WithMany()
                .HasForeignKey(l => l.ProductServiceId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(l => l.LeadSource)
                .WithMany()
                .HasForeignKey(l => l.LeadSourceId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(l => l.CreatedByUser)
                .WithMany()
                .HasForeignKey(l => l.CreatedByUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(l => l.AssignedUser)
                .WithMany()
                .HasForeignKey(l => l.AssignedUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(l => l.OfficeLocation)
                .WithMany()
                .HasForeignKey(l => l.OfficeLocationId)
                .OnDelete(DeleteBehavior.SetNull);

            e.HasIndex(l => new { l.CompanyId, l.OfficeLocationId, l.LeadStatus });
            e.HasIndex(l => new { l.OfficeLocationId, l.AssignedUserId });
            e.HasIndex(l => l.NextFollowUpDate).HasFilter("[IsActive] = 1");
        });

        modelBuilder.Entity<CrmLeadAssignment>(e =>
        {
            e.ToTable("myonline_tbl_CRM_LeadAssignments");
            e.HasKey(a => a.AssignmentId);
            e.Property(a => a.Remarks).HasMaxLength(500);

            e.HasOne(a => a.Company)
                .WithMany()
                .HasForeignKey(a => a.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(a => a.Lead)
                .WithMany(l => l.Assignments)
                .HasForeignKey(a => a.LeadId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(a => a.PreviousUser)
                .WithMany()
                .HasForeignKey(a => a.PreviousUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(a => a.NewUser)
                .WithMany()
                .HasForeignKey(a => a.NewUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(a => a.AssignedByUser)
                .WithMany()
                .HasForeignKey(a => a.AssignedByUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(a => a.OfficeLocation)
                .WithMany()
                .HasForeignKey(a => a.OfficeLocationId)
                .OnDelete(DeleteBehavior.SetNull);

            e.HasIndex(a => a.OfficeLocationId);
        });

        modelBuilder.Entity<CrmLeadFollowUp>(e =>
        {
            e.ToTable("myonline_tbl_CRM_LeadFollowUps");
            e.HasKey(f => f.FollowUpId);
            e.Property(f => f.Status).HasMaxLength(50);

            e.HasOne(f => f.Company)
                .WithMany()
                .HasForeignKey(f => f.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(f => f.Lead)
                .WithMany(l => l.FollowUps)
                .HasForeignKey(f => f.LeadId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(f => f.CreatedByUser)
                .WithMany()
                .HasForeignKey(f => f.CreatedByUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(f => f.OfficeLocation)
                .WithMany()
                .HasForeignKey(f => f.OfficeLocationId)
                .OnDelete(DeleteBehavior.SetNull);

            e.HasIndex(f => new { f.OfficeLocationId, f.CreatedByUserId });
        });

        modelBuilder.Entity<CrmLeadRemark>(e =>
        {
            e.ToTable("myonline_tbl_CRM_LeadRemarks");
            e.HasKey(r => r.RemarkId);

            e.HasOne(r => r.Company)
                .WithMany()
                .HasForeignKey(r => r.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(r => r.Lead)
                .WithMany(l => l.RemarksHistory)
                .HasForeignKey(r => r.LeadId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(r => r.User)
                .WithMany()
                .HasForeignKey(r => r.UserId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<CrmKpi>(e =>
        {
            e.ToTable("myonline_tbl_CRM_KPI");
            e.HasKey(k => k.KpiId);
            e.Property(k => k.PeriodType).HasMaxLength(20);

            e.HasOne(k => k.Company)
                .WithMany()
                .HasForeignKey(k => k.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(k => k.User)
                .WithMany()
                .HasForeignKey(k => k.UserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(k => k.CreatedByUser)
                .WithMany()
                .HasForeignKey(k => k.CreatedByUserId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(k => k.OfficeLocation)
                .WithMany()
                .HasForeignKey(k => k.OfficeLocationId)
                .OnDelete(DeleteBehavior.SetNull);

            e.HasIndex(k => new { k.CompanyId, k.OfficeLocationId, k.UserId });
        });

        modelBuilder.Entity<CrmLeadStatusHistory>(e =>
        {
            e.ToTable("myonline_tbl_CRM_LeadStatusHistory");
            e.HasKey(h => h.StatusHistoryId);
            e.Property(h => h.PreviousStatus).HasMaxLength(50);
            e.Property(h => h.NewStatus).HasMaxLength(50);
            e.HasIndex(h => new { h.LeadId, h.ChangedDateUtc });

            e.HasOne(h => h.Company)
                .WithMany()
                .HasForeignKey(h => h.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(h => h.Lead)
                .WithMany(l => l.StatusHistory)
                .HasForeignKey(h => h.LeadId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(h => h.ChangedByUser)
                .WithMany()
                .HasForeignKey(h => h.ChangedByUserId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<CrmAuditLog>(e =>
        {
            e.ToTable("myonline_tbl_CRM_AuditLog");
            e.HasKey(a => a.AuditLogId);
            e.Property(a => a.Action).HasMaxLength(50);
            e.Property(a => a.EntityType).HasMaxLength(30);
            e.HasIndex(a => new { a.CompanyId, a.EntityType, a.EntityId });

            e.HasOne(a => a.Company)
                .WithMany()
                .HasForeignKey(a => a.CompanyId)
                .OnDelete(DeleteBehavior.Restrict);

            e.HasOne(a => a.User)
                .WithMany()
                .HasForeignKey(a => a.UserId)
                .OnDelete(DeleteBehavior.Restrict);
        });
    }
}
