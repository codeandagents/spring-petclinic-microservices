# Rebuilds one PetClinic service and restarts it, so the running app picks up a code change.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File restart-service.ps1 customers-service
#
# Why not just stop and start the java process: a killed instance stays registered in Eureka
# (about 90 s) and the gateway keeps sending calls to it, which fail (405 from the fallback, 503).
# So the old instance is removed from Eureka right after the stop, and the script only reports "ready" once the
# gateway has routed 10 calls in a row to the new one.

param(
    [Parameter(Mandatory)]
    [ValidateSet("customers-service", "vets-service", "visits-service", "api-gateway")]
    [string]$Service,
    [string]$Repo   # default: the repo this skill lives in (.claude/skills/restart-service/scripts)
)

$ErrorActionPreference = "Stop"
if (-not $Repo) { $Repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path }
if (-not (Test-Path "$Repo\mvnw.cmd")) { throw "no mvnw.cmd in $Repo" }
$module = "spring-petclinic-$Service"
$app = $Service.ToUpper()
$eureka = "http://localhost:8761/eureka/apps"
$probe = @{
    "customers-service" = "http://localhost:8080/api/customer/owners/1"
    "vets-service"      = "http://localhost:8080/api/vet/vets"
    "visits-service"    = "http://localhost:8080/api/visit/pets/visits?petId=7"
    "api-gateway"       = "http://localhost:8080/"
}[$Service]

function Say($text) { Write-Host "  $text" }

function Hide-User($text) { $text -replace [regex]::Escape($env:USERNAME), "<user>" }

function Get-Instances {
    try {
        $r = Invoke-RestMethod -Headers @{Accept = "application/json"} "$eureka/$app"
        @($r.application.instance | ForEach-Object { $_.instanceId })
    } catch { @() }
}

function Get-Pids {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -like "*$module-*.jar*" } | ForEach-Object { $_.ProcessId }
}

Write-Host "restart-service: $Service"

# Stop first, then deregister: a live instance would register again at its next heartbeat.
$old = Get-Instances
$pids = @(Get-Pids)
$pids | ForEach-Object { Stop-Process -Id $_ -Force }
Say "stopped $($pids.Count) process(es)"
foreach ($id in $old) { Invoke-RestMethod -Method Delete "$eureka/$app/$id" | Out-Null }
Say "removed $($old.Count) instance(s) from Eureka"

# cmd merges Maven's stderr (JVM warnings) into stdout: Windows PowerShell would stop on it.
$build = cmd /c "cd /d `"$Repo`" && .\mvnw.cmd -B -ntp -q package -DskipTests -pl $module 2>&1"
if ($LASTEXITCODE) {
    $build | Select-Object -Last 30 | ForEach-Object { Hide-User $_ }
    throw "build failed: $Service is NOT running"
}
$jar = Get-ChildItem "$Repo\$module\target\$module-*.jar" | Select-Object -First 1
Say "built $($jar.Name)"

$log = Join-Path $env:TEMP "petclinic-$Service.log"
$proc = Start-Process -PassThru -WindowStyle Hidden -FilePath "java" -ArgumentList "-jar", $jar.FullName `
    -WorkingDirectory $Repo -RedirectStandardOutput $log -RedirectStandardError "$log.err"
Say "started PID $($proc.Id), log: %TEMP%\petclinic-$Service.log"

for ($i = 0; $i -lt 90 -and -not (Get-Instances); $i++) {
    if ($proc.HasExited) { break }
    Start-Sleep 2
}
if (-not (Get-Instances)) {
    Get-Content $log -Tail 20 | ForEach-Object { Hide-User $_ }
    throw "$Service did not register with Eureka"
}
Say "registered in Eureka"

if ($Service -ne "api-gateway") {
    try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:8080/actuator/health" | Out-Null }
    catch {
        Say "ready: $Service is registered, but api-gateway is not running, so no call went through it"
        exit 0
    }
}
$ok = 0
for ($i = 0; $i -lt 120 -and $ok -lt 10; $i++) {
    try { $code = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 $probe).StatusCode } catch { $code = 0 }
    if ($code -eq 200) { $ok++ } else { $ok = 0; Start-Sleep 2 }
}
if ($ok -lt 10) { throw "the gateway never routed 10 calls in a row to $Service ($probe)" }
Say "ready: the gateway routed 10 calls in a row to it"
