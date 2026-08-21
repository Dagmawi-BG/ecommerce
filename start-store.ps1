# ============================================================================
# start-store.ps1 - one-click launcher for the e-commerce store.
# Starts (in order): PostgreSQL service, Keycloak, then the Spring Boot app,
# waiting for each to be ready, and finally opens the storefront in a browser.
# ============================================================================

# ---- Config (edit these if your paths/ports differ) ------------------------
$JdkHome      = 'C:\Program Files\jdk-21.0.6'
$KeycloakBat  = 'C:\keycloak\bin\kc.bat'
$KeycloakPort = 8180
$ProjectPom   = 'C:\Users\user\e-commerce\pom.xml'
$AppPort      = 8080
$AppUrl       = "http://localhost:$AppPort"
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Continue'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn2($msg){ Write-Host "    [!] $msg" -ForegroundColor Yellow }

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); $client.Close(); return $true }
    catch { return $false }
}

# Considers the server up as soon as it answers at all - even a 302/401 counts.
function Wait-HttpReady([string]$Url, [int]$TimeoutSec = 150) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 | Out-Null
            return $true
        } catch {
            if ($_.Exception.Response) { return $true }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

Write-Host "==========================================" -ForegroundColor Magenta
Write-Host "  E-Commerce Store - one-click launcher"    -ForegroundColor Magenta
Write-Host "==========================================" -ForegroundColor Magenta

# ---- Java 21 for every child process (avoids the Java 8 on PATH) -----------
Write-Step "Selecting Java 21"
if (Test-Path $JdkHome) {
    $env:JAVA_HOME = $JdkHome
    $env:Path = "$JdkHome\bin;$env:Path"
    Write-Ok "JAVA_HOME = $JdkHome"
} else {
    Write-Warn2 "JDK 21 not found at $JdkHome - relying on existing JAVA_HOME ($env:JAVA_HOME)."
}

# ---- 1. PostgreSQL ---------------------------------------------------------
Write-Step "PostgreSQL (port 5432)"
if (Test-Port 5432) {
    Write-Ok "Already running."
} else {
    $pg = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pg) {
        try {
            Start-Service $pg.Name -ErrorAction Stop
            Write-Ok "Started service '$($pg.Name)'."
        } catch {
            Write-Warn2 "Could not start '$($pg.Name)' (try running this script as Administrator)."
        }
    } else {
        Write-Warn2 "No 'postgresql*' service found - start PostgreSQL manually."
    }
}

# ---- 2. Keycloak -----------------------------------------------------------
Write-Step "Keycloak (port $KeycloakPort)"
if (Test-Port $KeycloakPort) {
    Write-Ok "Already running."
} elseif (Test-Path $KeycloakBat) {
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', "`"$KeycloakBat`" start-dev --http-port $KeycloakPort" -WindowStyle Normal
    Write-Host "    Waiting for Keycloak to come up..." -ForegroundColor Gray
    if (Wait-HttpReady "http://localhost:$KeycloakPort/") {
        Write-Ok "Keycloak is ready."
    } else {
        Write-Warn2 "Keycloak did not respond in time - check its window."
    }
} else {
    Write-Warn2 "kc.bat not found at $KeycloakBat - start Keycloak manually."
}

# ---- 3. The Spring Boot app ------------------------------------------------
Write-Step "Store application (port $AppPort)"
if (Test-Port $AppPort) {
    Write-Ok "Already running."
} else {
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', "mvn -f `"$ProjectPom`" spring-boot:run" -WindowStyle Normal
    Write-Host "    Waiting for the app to come up (first run compiles)..." -ForegroundColor Gray
    if (Wait-HttpReady "$AppUrl/products") {
        Write-Ok "Store is ready."
    } else {
        Write-Warn2 "App did not respond in time - check its window."
    }
}

# ---- Open the storefront ---------------------------------------------------
Write-Step "Opening $AppUrl"
Start-Process $AppUrl

Write-Host "`nAll set. Keycloak and the app run in their own windows;" -ForegroundColor Magenta
Write-Host "close those windows to stop them.`n" -ForegroundColor Magenta
