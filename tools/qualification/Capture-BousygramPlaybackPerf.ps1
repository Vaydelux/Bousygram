#requires -Version 5.1
<#
.SYNOPSIS
Capture sanitized playback performance observations from one connected Android device.
.DESCRIPTION
Run while Bousygram is playing a known test clip. No screenshots, private
Telegram content, credentials, raw logcat or device serials are written.
This is developer diagnostic evidence, NOT a production qualification result:
dumpsys gfxinfo measures app UI frames, NOT video decoder dropped frames.
.EXAMPLE
.\tools\qualification\Capture-BousygramPlaybackPerf.ps1 -SampleSeconds 60 -OutputDirectory .\perf-evidence
#>
[CmdletBinding()]
param(
    [ValidateRange(10, 3600)]
    [int] $SampleSeconds = 60,
    [string] $DeviceSerial,
    [string] $OutputDirectory = ".\perf-evidence"
)

$ErrorActionPreference = "Stop"
$packageName = "com.vaydelux.bousygram"
$adbPath = (Get-Command adb -ErrorAction Stop).Source
$devices = @(& $adbPath devices | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
if ($devices.Count -eq 0) { throw "No authorized Android device found. Enable USB debugging and run adb devices." }
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($devices.Count -ne 1) { throw "Multiple devices connected; specify -DeviceSerial." }
    $DeviceSerial = ($devices[0] -split '\s+')[0]
}
if (@($devices | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial }).Count -ne 1) {
    throw "Selected ADB device is not connected and authorized."
}

$adbSelection = @("-s", $DeviceSerial)
function Invoke-SelectedAdb {
    param([string[]] $CommandArguments)
    $lines = & $adbPath @adbSelection @CommandArguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: $($CommandArguments -join ' ')"
    }
    return (($lines | Out-String).Trim())
}

function Get-Number {
    param([string] $Text, [string] $Pattern)
    $match = [regex]::Match($Text, $Pattern)
    if (-not $match.Success) { return $null }
    return [long]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}

$installedPaths = Invoke-SelectedAdb -CommandArguments @("shell", "pm", "path", $packageName)
if ($installedPaths -notmatch 'package:') { throw "Bousygram is not installed on the selected device." }
$pidValue = Invoke-SelectedAdb -CommandArguments @("shell", "pidof", $packageName)
if ($pidValue -notmatch '^\d+') { throw "Launch Bousygram and begin playing media before recording performance." }

$manufacturer = Invoke-SelectedAdb -CommandArguments @("shell", "getprop", "ro.product.manufacturer")
$model = Invoke-SelectedAdb -CommandArguments @("shell", "getprop", "ro.product.model")
$androidVersion = Invoke-SelectedAdb -CommandArguments @("shell", "getprop", "ro.build.version.release")
$abi = Invoke-SelectedAdb -CommandArguments @("shell", "getprop", "ro.product.cpu.abi")
$startedUtc = (Get-Date).ToUniversalTime().ToString("o")

# Reset *only the application frame counters* so the counts apply to this
# sampling window. No app data, cache, auth state or media files are touched.
$startMeminfo = Invoke-SelectedAdb -CommandArguments @("shell", "dumpsys", "meminfo", $packageName)
$null = Invoke-SelectedAdb -CommandArguments @("shell", "dumpsys", "gfxinfo", $packageName, "reset")
Write-Host "Sampling Bousygram UI frames and memory for $SampleSeconds seconds. Keep a test clip playing."
Start-Sleep -Seconds $SampleSeconds
$endMeminfo = Invoke-SelectedAdb -CommandArguments @("shell", "dumpsys", "meminfo", $packageName)
$gfxinfo = Invoke-SelectedAdb -CommandArguments @("shell", "dumpsys", "gfxinfo", $packageName)
$endedUtc = (Get-Date).ToUniversalTime().ToString("o")

$beforePss = Get-Number -Text $startMeminfo -Pattern '(?im)^\s*TOTAL PSS:\s*(\d+)'
$afterPss = Get-Number -Text $endMeminfo -Pattern '(?im)^\s*TOTAL PSS:\s*(\d+)'
$totalFrames = Get-Number -Text $gfxinfo -Pattern '(?im)^\s*Total frames rendered:\s*(\d+)'
$jankyFrames = Get-Number -Text $gfxinfo -Pattern '(?im)^\s*Janky frames:\s*(\d+)'
$jankRatio = $null
if ($null -ne $totalFrames -and $totalFrames -gt 0 -and $null -ne $jankyFrames) {
    $jankRatio = [Math]::Round(100.0 * $jankyFrames / $totalFrames, 2)
}

$report = [ordered]@{
    schemaVersion = 1
    status = "observations-only"
    productionQualified = $false
    hardwarePlaybackVerified = $false
    packageName = $packageName
    sampleDurationRequestedSeconds = $SampleSeconds
    startedUtc = $startedUtc
    endedUtc = $endedUtc
    device = [ordered]@{
        manufacturer = $manufacturer
        model = $model
        androidVersion = $androidVersion
        abi = $abi
    }
    applicationMemory = [ordered]@{
        initialTotalPssKiB = $beforePss
        finalTotalPssKiB = $afterPss
    }
    composeUiFrames = [ordered]@{
        totalFrames = $totalFrames
        jankyFrames = $jankyFrames
        jankyPercent = $jankRatio
    }
    playbackDecoderDroppedFrames = $null
    firstFrameLatencyP95Ms = $null
    rebufferRatio = $null
    notes = "UI gfxinfo is not video decoder telemetry. Complete actual clip/ABI/decoder and startup/seek/rebuffer observations separately. Missing fields are unknown, not zero."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$destination = Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path "bousygram-playback-perf-$stamp.json"
[IO.File]::WriteAllText($destination,
    (($report | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
    (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Sanitized device report: $destination"
Write-Host "UI frames: $totalFrames | janky: $jankyFrames | UI jank percentage: $jankRatio"
Write-Host "Video decoder quality and actual playback still require device review."
