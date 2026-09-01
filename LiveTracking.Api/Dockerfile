FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080
ENV ASPNETCORE_URLS=http://+:8080

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY ["LiveTracking.Api.csproj", "./"]
RUN dotnet restore "LiveTracking.Api.csproj"
COPY . .
RUN dotnet build "LiveTracking.Api.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "LiveTracking.Api.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "LiveTracking.Api.dll"]
