using System.Text;
using LiveTracking.Api.Data;
using LiveTracking.Api.Hubs;
using LiveTracking.Api.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new Microsoft.OpenApi.Models.OpenApiInfo
    {
        Title = "Live Tracking API",
        Version = "v1",
        Description = "Live Tracking System Backend Web API"
    });

    c.AddSecurityDefinition("Bearer", new Microsoft.OpenApi.Models.OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = Microsoft.OpenApi.Models.SecuritySchemeType.ApiKey,
        Scheme = "Bearer",
        BearerFormat = "JWT",
        In = Microsoft.OpenApi.Models.ParameterLocation.Header,
        Description = "Enter 'Bearer' [space] and then your token.\r\nExample: \"Bearer eyJhbGciOiJIUzI1Ni...\""
    });

    c.AddSecurityRequirement(new Microsoft.OpenApi.Models.OpenApiSecurityRequirement
    {
        {
            new Microsoft.OpenApi.Models.OpenApiSecurityScheme
            {
                Reference = new Microsoft.OpenApi.Models.OpenApiReference
                {
                    Type = Microsoft.OpenApi.Models.ReferenceType.SecurityScheme,
                    Id = "Bearer"
                }
            },
            Array.Empty<string>()
        }
    });

    c.CustomSchemaIds(type =>
    {
        if (type.IsGenericType)
        {
            var genericArgs = string.Join("_", type.GetGenericArguments().Select(t => t.Name));
            return $"{type.Name.Split('`')[0]}_{genericArgs}";
        }
        return (type.FullName ?? type.Name).Replace("+", ".");
    });
});

builder.Services.AddDbContext<LiveTrackingDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection"), sqlOptions =>
    {
        sqlOptions.UseCompatibilityLevel(120);
        sqlOptions.EnableRetryOnFailure(maxRetryCount: 3, maxRetryDelay: TimeSpan.FromSeconds(5), errorNumbersToAdd: null);
    }));

builder.Services.AddScoped<IJwtTokenService, JwtTokenService>();
builder.Services.AddScoped<IPasswordHasherService, PasswordHasherService>();
builder.Services.AddScoped<ICrmService, CrmService>();
builder.Services.AddScoped<LiveTracking.Api.Repositories.CRM.ICrmLeadRepository, LiveTracking.Api.Repositories.CRM.CrmLeadRepository>();
builder.Services.AddHostedService<SubscriptionReminderHostedService>();
builder.Services.AddHostedService<AttendanceShiftHostedService>();
builder.Services.AddHostedService<CrmFollowUpReminderHostedService>();

builder.Services.AddSignalR();

var jwtSection = builder.Configuration.GetSection("Jwt");
var jwtKey = jwtSection["Key"]!;

builder.Services.AddAuthentication(options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
})
.AddJwtBearer(options =>
{
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true,
        ValidateAudience = true,
        ValidateLifetime = true,
        ValidateIssuerSigningKey = true,
        ValidIssuer = jwtSection["Issuer"],
        ValidAudience = jwtSection["Audience"],
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey))
    };

    // Allow SignalR to receive the JWT via query string for websocket connections.
    options.Events = new JwtBearerEvents
    {
        OnMessageReceived = context =>
        {
            var accessToken = context.Request.Query["access_token"];
            var path = context.HttpContext.Request.Path;
            if (!string.IsNullOrEmpty(accessToken) && path.StartsWithSegments("/hubs/location"))
            {
                context.Token = accessToken;
            }
            return Task.CompletedTask;
        }
    };
});

builder.Services.AddAuthorization();

builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAll", policy =>
        policy.AllowAnyHeader().AllowAnyMethod().SetIsOriginAllowed(_ => true).AllowCredentials());
});

// Ensure static files directory exists
var contentRoot = builder.Environment.ContentRootPath;
var webRoot = Path.Combine(contentRoot, "wwwroot");
var uploadsRoot = Path.Combine(contentRoot, "uploads");

try
{
    if (!Directory.Exists(webRoot)) Directory.CreateDirectory(webRoot);
    builder.Environment.WebRootPath = webRoot;

    // Ensure external uploads directory exists outside wwwroot
    if (!Directory.Exists(uploadsRoot)) Directory.CreateDirectory(uploadsRoot);
    var selfiesDir = Path.Combine(uploadsRoot, "selfies");
    if (!Directory.Exists(selfiesDir)) Directory.CreateDirectory(selfiesDir);
    var companiesDir = Path.Combine(uploadsRoot, "companies");
    if (!Directory.Exists(companiesDir)) Directory.CreateDirectory(companiesDir);
}
catch (Exception ex)
{
    Console.WriteLine($"[Storage Warning] Error ensuring directories on startup: {ex.Message}");
}

var app = builder.Build();

// Enable Swagger UI in published (Production) mode as well
app.UseSwagger();
app.UseSwaggerUI(options =>
{
    options.SwaggerEndpoint("v1/swagger.json", "Live Tracking API v1");
    options.RoutePrefix = "swagger";
});

app.UseCors("AllowAll");

// Serve static files from wwwroot
app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new Microsoft.Extensions.FileProviders.PhysicalFileProvider(webRoot),
    RequestPath = ""
});

// Serve uploads directly from external directory outside wwwroot
app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new Microsoft.Extensions.FileProviders.PhysicalFileProvider(uploadsRoot),
    RequestPath = "/uploads"
});

// Direct streaming endpoint for uploads (Selfie images, proof attachments, etc.)
app.MapGet("/uploads/{**filePath}", (string filePath, IWebHostEnvironment env) =>
{
    if (string.IsNullOrWhiteSpace(filePath)) return Results.NotFound();

    var cleanPath = filePath.TrimStart('/', '\\').Replace('\\', '/');
    var fileName = Path.GetFileName(cleanPath);

    var candidates = new[]
    {
        Path.Combine(env.ContentRootPath, "uploads", cleanPath),
        Path.Combine(env.ContentRootPath, "uploads", "selfies", fileName),
        Path.Combine(AppContext.BaseDirectory, "uploads", cleanPath),
        Path.Combine(AppContext.BaseDirectory, "uploads", "selfies", fileName),
        Path.Combine(Directory.GetCurrentDirectory(), "uploads", cleanPath),
        Path.Combine(Directory.GetCurrentDirectory(), "uploads", "selfies", fileName),
        Path.Combine(env.ContentRootPath, "wwwroot", "uploads", cleanPath),
        Path.Combine(env.ContentRootPath, "wwwroot", "uploads", "selfies", fileName),
        Path.Combine(AppContext.BaseDirectory, "wwwroot", "uploads", cleanPath),
        Path.Combine(AppContext.BaseDirectory, "wwwroot", "uploads", "selfies", fileName),
        Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "uploads", cleanPath),
        Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "uploads", "selfies", fileName)
    };

    foreach (var path in candidates)
    {
        if (System.IO.File.Exists(path))
        {
            var ext = Path.GetExtension(path).ToLowerInvariant();
            var contentType = ext switch
            {
                ".jpg" or ".jpeg" => "image/jpeg",
                ".png" => "image/png",
                ".webp" => "image/webp",
                ".apk" => "application/vnd.android.package-archive",
                _ => "application/octet-stream"
            };
            return Results.File(path, contentType);
        }
    }

    return Results.NotFound();
}).AllowAnonymous();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapHub<LocationHub>("/hubs/location");

app.Run();
