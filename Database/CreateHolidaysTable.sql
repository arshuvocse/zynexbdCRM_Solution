-- Create Holidays Table
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'myonline_tbl_Holidays')
BEGIN
    CREATE TABLE myonline_tbl_Holidays (
        HolidayId INT IDENTITY(1,1) PRIMARY KEY,
        Name NVARCHAR(150) NOT NULL,
        Date DATETIME2 NOT NULL,
        Year INT NOT NULL,
        IsRecurring BIT NOT NULL DEFAULT 0,
        IsActive BIT NOT NULL DEFAULT 1,
        Description NVARCHAR(250) NULL
    );

    -- Seed 2026 Bangladesh Govt Holidays
    INSERT INTO myonline_tbl_Holidays (Name, Date, Year, IsRecurring, IsActive, Description)
    VALUES 
        ('International Mother Language Day', '2026-02-21', 2026, 1, 1, 'National Holiday'),
        ('Shab-e-Barat', '2026-03-20', 2026, 0, 1, 'Religious Holiday'),
        ('Independence Day', '2026-03-26', 2026, 1, 1, 'National Holiday'),
        ('Eid-ul-Fitr Holiday', '2026-03-29', 2026, 0, 1, 'Public Holiday'),
        ('Eid-ul-Fitr', '2026-03-30', 2026, 0, 1, 'Public Holiday'),
        ('Eid-ul-Fitr Holiday', '2026-03-31', 2026, 0, 1, 'Public Holiday'),
        ('Pahela Baishakh', '2026-04-14', 2026, 1, 1, 'Bengali New Year'),
        ('May Day', '2026-05-01', 2026, 1, 1, 'International Workers Day'),
        ('Buddha Purnima', '2026-05-31', 2026, 0, 1, 'Religious Holiday'),
        ('Eid-ul-Adha Holiday', '2026-06-06', 2026, 0, 1, 'Public Holiday'),
        ('Eid-ul-Adha', '2026-06-07', 2026, 0, 1, 'Public Holiday'),
        ('Eid-ul-Adha Holiday', '2026-06-08', 2026, 0, 1, 'Public Holiday'),
        ('Ashura', '2026-07-06', 2026, 0, 1, 'Religious Holiday'),
        ('National Mourning Day', '2026-08-15', 2026, 1, 1, 'National Holiday'),
        ('Janmashtami', '2026-08-25', 2026, 0, 1, 'Religious Holiday'),
        ('Eid-e-Miladunnabi', '2026-09-04', 2026, 0, 1, 'Religious Holiday'),
        ('Durga Puja', '2026-10-20', 2026, 0, 1, 'Religious Holiday'),
        ('Victory Day', '2026-12-16', 2026, 1, 1, 'National Holiday'),
        ('Christmas Day', '2026-12-25', 2026, 1, 1, 'Religious Holiday');
END
GO
