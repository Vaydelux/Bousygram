#requires -Version 5.1
<#
.SYNOPSIS
Capture a privacy-conscious starting record for one *physical* Android device.
.DESCRIPTION
Run against a verified, production-signed Bousygram release candidate. This
script never marks a test passed and never collects Telegram content, logcat,
device serial numbers, or auth tokens. Use -Install to install the exact APK
without uninstalling existing user data, and test playback manually.
.EXAMPLE
.\tools\qualification\Capture-BousygramDevice.ps1 -ApkPath .\release-artifacts\Bousygram-v1.2.3-hardenedRelease-arm64-v8a.apk -Install
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [string] $ApkPath,
    [string] $DeviceSerial,
    [string] $OutputDirectory = ".\qualification-evidence",
    [switch] $Install
)
$ErrorActionPreference = "Stop"
$apk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
if ($apk -notmatch '\.apk$') { throw "Input must be a signed .apk file." }
if ([IO.Path]::GetFileName($apk) -match '(?i)debug') {
    throw "Debug-signed APKs cannot be used for production device qualification."
}
$adbCmd = Get-Command adb -ErrorAction Stop
$adbPath = $adbCmd.Source
$deviceLines = @(& $adbPath devices | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
if ($deviceLines.Count -eq 0) { throw "No authorized physical Android device found with adb devices." }
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($deviceLines.Count -ne 1) { throw "More than one device is connected; supply -DeviceSerial." }
    $DeviceSerial = ($deviceLines[0] -split '\s+')[0]
}
$selectedLine = @($deviceLines | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial })
if ($selectedLine.Count -ne 1) { throw "The specified ADB device is not connected and authorized." }
$adbSelection = @("-s", $DeviceSerial)
function Invoke-DeviceAdb {
    param([string[]] $Arguments)
    $result = & $adbPath @adbSelection @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($Arguments -join ' ')" }
    return (($result | Out-String).Trim())
}
$model = Invoke-DeviceAdb @("shell", "getprop", "ro.product.model")
$android = Invoke-DeviceAdb @("shell", "getprop", "ro.build.version.release")
$sdk = Invoke-DeviceAdb @("shell", "getprop", "ro.build.version.sdk")
$abi = Invoke-DeviceAdb @("shell", "getprop", "ro.product.cpu.abi")
$manufacturer = Invoke-DeviceAdb @("shell", "getprop", "ro.product.manufacturer")
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
$installedFromCandidate = $false
if ($Install) {
    $installOutput = Invoke-DeviceAdb @("install", "-r", $apk)
    if ($installOutput -notmatch "Success") {
        throw "APK install did not succeed. Device may have a different signing certificate. Do NOT uninstall automatically."
    }
    $installedFromCandidate = $true
}
$package = "com.vaydelux.bousygram"
$installed = $false
try {
    $paths = Invoke-DeviceAdb @("shell", "pm", "path", $package)
    $installed = $paths -match 'package:'
} catch { $installed = $false }
$report = [ordered]@{
    status = "not-run"
    productionQualification = $false
    testedApkSha256 = $apkHash
    installedFromThisExactCandidate = $installedFromCandidate
    packageInstalled = $installed
    packageName = $package
    device = [ordered]@{
        manufacturer = $manufacturer
        model = $model
        androidVersion = $android
        sdk = $sdk
        abi = $abi
    }
    testIterations = 0
    nativeTdlibRuntime = "not-run"
    manualChecks = [ordered]@{
        authenticatedStartup = "not-run"
        mkvTsSeek = "not-run"
        audioOnly = "not-run"
        mpvFallback = "not-run"
        forceStopReopen = "not-run"
        networkRecovery = "not-run"
        accountIsolation = "not-run"
        catalogNavigation = "not-run"
        lifecycle = "not-run"
        longSession = "not-run"
    }
    notes = "Manual evidence and human review required. No checks automatically passed."
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$out = Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path ("device-" + $abi + "-" + $apkHash.Substring(0,12) + ".json")
$json = $report | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText($out, $json + [Environment]::NewLine, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Device baseline record: $out"
Write-Host "Candidate SHA-256: $apkHash"
Write-Host "Device: $manufacturer $model / Android $android / $abi"
Write-Host "This record is NOT a production approval. Run the authenticated playback checklist and review evidence."
