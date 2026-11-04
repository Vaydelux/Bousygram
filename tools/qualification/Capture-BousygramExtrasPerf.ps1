#requires -Version 5.1
<#
.SYNOPSIS
Record privacy-conscious Android memory/frames while Bousygram Extras is visible.
.DESCRIPTION
Navigate the app manually to SELECT, EXTRAS or TELEGRAM before running.
This script DOES NOT simulate browsing, infer a performance improvement, or
qualify a release. It records only aggregate memory and UI frame counters.
It never saves ADB serials, logcat, Telegram content or provider responses.
.EXAMPLE
.\tools\qualification\Capture-BousygramExtrasPerf.ps1 -Stage EXTRAS -Samples 5
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("SELECT", "EXTRAS", "TELEGRAM")]
    [string] $Stage,
    [ValidateRange(1, 30)]
    [int] $Samples = 5,
    [ValidateRange(1, 30)]
    [int] $IntervalSeconds = 2,
    [string] $DeviceSerial,
    [string] $OutputDirectory = ".\qualification-evidence"
)
$ErrorActionPreference = "Stop"
$adb = (Get-Command adb -ErrorAction Stop).Source
$devices = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
if ($devices.Count -eq 0) { throw "No authorized ADB device." }
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($devices.Count -ne 1) {
        throw "Multiple devices connected. Pass -DeviceSerial explicitly."
    }
    $DeviceSerial = ($devices[0] -split '\s+')[0]
}
if (@($devices | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial }).Count -ne 1) {
    throw "Selected device is not connected or authorized."
}
function Invoke-SelectedAdb {
    param([string[]] $Arguments)
    $output = & $adb -s $DeviceSerial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed; verify the device and permissions."
    }
    return (($output | Out-String).Trim())
}
$package = "com.vaydelux.bousygram"
$abi = Invoke-SelectedAdb @("shell", "getprop", "ro.product.cpu.abi")
$android = Invoke-SelectedAdb @("shell", "getprop", "ro.build.version.release")
$records = @()
for ($i = 1; $i -le $Samples; $i++) {
    $pidText = Invoke-SelectedAdb @("shell", "pidof", "-s", $package)
    if ($pidText -notmatch '^\d+$') {
        throw "Bousygram process is not active. Open the $Stage screen first."
    }
    $mem = Invoke-SelectedAdb @("shell", "dumpsys", "meminfo", $package)
    $pss = [regex]::Match($mem, '(?m)^\s*TOTAL PSS:\s*([\d,]+)')
    if (-not $pss.Success) {
        $pss = [regex]::Match($mem, '(?m)^\s*TOTAL\s+([\d,]+)\s+')
    }
    if (-not $pss.Success) {
        throw "Unable to parse TOTAL PSS from this Android version's dumpsys output."
    }
    $pssKb = [long]($pss.Groups[1].Value -replace ',', '')
    $gfx = Invoke-SelectedAdb @("shell", "dumpsys", "gfxinfo", $package)
    $frames = [regex]::Match($gfx, 'Total frames rendered:\s*([\d,]+)')
    $jank = [regex]::Match($gfx, 'Janky frames:\s*([\d,]+)')
    $totalFrames = if ($frames.Success) { [long]($frames.Groups[1].Value -replace ',', '') } else { $null }
    $jankyFrames = if ($jank.Success) { [long]($jank.Groups[1].Value -replace ',', '') } else { $null }
    $records += [pscustomobject]@{
        sample = $i
        capturedUtc = (Get-Date).ToUniversalTime().ToString("o")
        totalPssKb = $pssKb
        totalFramesCumulative = $totalFrames
        jankyFramesCumulative = $jankyFrames
    }
    if ($i -lt $Samples) { Start-Sleep -Seconds $IntervalSeconds }
}
$stats = $records | Measure-Object -Property totalPssKb -Average -Minimum -Maximum
$report = [ordered]@{
    status = "captured-only"
    productionQualification = $false
    targetScreen = $Stage
    device = [ordered]@{ abi = $abi; androidVersion = $android }
    packageName = $package
    note = "Navigate to the selected screen yourself. Compare repeat runs; this is not a pass/fail result."
    sampleCount = $records.Count
    averagePssKb = [math]::Round($stats.Average, 1)
    minPssKb = [long]$stats.Minimum
    maxPssKb = [long]$stats.Maximum
    samples = $records
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$destination = (Resolve-Path -LiteralPath $OutputDirectory).Path
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$out = Join-Path $destination ("extras-" + $Stage.ToLowerInvariant() + "-" + $abi + "-" + $stamp + ".json")
[IO.File]::WriteAllText(
    $out,
    (($report | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
    (New-Object System.Text.UTF8Encoding($false))
)
Write-Host "Captured $($records.Count) aggregate samples for $Stage on $abi."
Write-Host "Average TOTAL PSS (KB): $([math]::Round($stats.Average, 1))"
Write-Host "Evidence (not release approval): $out"
