#requires -Version 5.1
<#
.SYNOPSIS
Collect privacy-minimized, repeated physical-device PSS observations.
.DESCRIPTION
The operator performs real authenticated Telegram reconnects, repeated playback,
or native MPV/LibVLC switching. This script samples process PSS/native heap
without reading logcat, private media, TDLib databases, URLs or account IDs.
It does NOT automatically label a scenario passed or qualify a release.
.EXAMPLE
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario arm32-playback -Cycles 20 -SampleSeconds 60
.EXAMPLE
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario tdlib-reconnect -Cycles 10
#>
[CmdletBinding()]
param(
    [ValidateSet('tdlib-reconnect', 'arm32-playback', 'native-engines')]
    [string] $Scenario = 'arm32-playback',
    [ValidateRange(2, 100)]
    [int] $Cycles = 20,
    [ValidateRange(1, 3600)]
    [int] $SampleSeconds = 30,
    [string] $DeviceSerial,
    [string] $ApkPath,
    [ValidatePattern('^[a-fA-F0-9]{40}$')]
    [string] $SourceCommit,
    [string] $OutputDirectory = '.\qualification-evidence'
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.vaydelux.bousygram'
$adb = (Get-Command adb -ErrorAction Stop).Source
$deviceLines = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
if ($deviceLines.Count -eq 0) { throw 'Connect and authorize a physical Android device with ADB.' }
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($deviceLines.Count -ne 1) { throw 'Multiple devices detected; specify -DeviceSerial.' }
    $DeviceSerial = ($deviceLines[0] -split '\s+')[0]
}
if (@($deviceLines | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial }).Count -ne 1) {
    throw 'Specified ADB device is not connected/authorized.'
}
$selection = @('-s', $DeviceSerial)

function Invoke-SelectedAdb {
    param([string[]] $CommandArguments)
    $value = & $adb @selection @CommandArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('ADB failed: ' + ($CommandArguments -join ' ')) }
    return (($value | Out-String).Trim())
}
function Get-MemoryNumber {
    param([string] $Source, [string] $Pattern)
    $match = [regex]::Match($Source, $Pattern)
    if (-not $match.Success) { return $null }
    return [long]::Parse(($match.Groups[1].Value -replace ',', ''), [Globalization.CultureInfo]::InvariantCulture)
}

$model = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.product.model')
$manufacturer = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.product.manufacturer')
$abi = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.product.cpu.abi')
$android = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.build.version.release')
$qemu = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.kernel.qemu')
if ($qemu -eq '1' -or $model -match '(?i)(sdk_gphone|emulator|android sdk built for)') {
    throw 'This soak tool requires a physical device, not an emulator.'
}
if ($abi -notin @('armeabi-v7a', 'arm64-v8a')) {
    throw "Expected ARM32 or ARM64 primary ABI; device reports $abi."
}
if ($Scenario -eq 'arm32-playback' -and $abi -ne 'armeabi-v7a') {
    throw 'The ARM32 playback scenario must run on an armeabi-v7a device.'
}
$installed = Invoke-SelectedAdb -CommandArguments @('shell', 'pm', 'path', $packageName)
if ($installed -notmatch 'package:') { throw 'Bousygram is not installed.' }

Write-Host "Device: $manufacturer $model / Android $android / $abi"
Write-Host 'No account/URL/media/logcat/serial will be saved. Use the SAME test clip, network and build for comparisons.'
Write-Host 'Manual playback and reconnection success is NOT inferred from PSS samples.'
if ($Scenario -eq 'tdlib-reconnect') {
    Write-Host 'Before each capture: reconnect Wi-Fi, return to Telegram mode, confirm the SAME authenticated account and attempt a real playback.'
} elseif ($Scenario -eq 'native-engines') {
    Write-Host 'Before each capture: alternate MPV/LibVLC using the in-app player or debug Engine Lab, play/seek/pause, and watch for crashes.'
} else {
    Write-Host 'Keep the ARM32 TV playing, repeat seeks and media switches through all capture intervals.'
}
$candidateApkSha256 = $null
if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    $candidateApk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
    if ([IO.Path]::GetExtension($candidateApk).ToLowerInvariant() -ne '.apk') {
        throw 'Candidate path must be an APK.'
    }
    $candidateApkSha256 = (Get-FileHash -LiteralPath $candidateApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host 'APK hash is operator-provided provenance only; this sampler cannot confirm the installed APK matches it.'
}
$startUtc = (Get-Date).ToUniversalTime().ToString('o')
$samples = @()
$aborted = $false
for ($i = 1; $i -le $Cycles; $i++) {
    if ($Scenario -ne 'arm32-playback') {
        $answer = Read-Host "Cycle $i/$Cycles: complete the on-device action, then press Enter to sample (type q to stop)"
        if ($answer -eq 'q') { $aborted = $true; break }
    }
    Start-Sleep -Seconds $SampleSeconds
    $pidText = Invoke-SelectedAdb -CommandArguments @('shell', 'pidof', $packageName)
    if ($pidText -notmatch '^\d+$') {
        throw "Bousygram is not running at cycle $i. Inspect for native crash/ANR; no passing report is written."
    }
    $meminfo = Invoke-SelectedAdb -CommandArguments @('shell', 'dumpsys', 'meminfo', $packageName)
    $pss = Get-MemoryNumber -Source $meminfo -Pattern '(?im)^\s*TOTAL PSS:\s*([\d,]+)'
    if ($null -eq $pss) {
        $pss = Get-MemoryNumber -Source $meminfo -Pattern '(?m)^\s*TOTAL\s+([\d,]+)\s+'
    }
    if ($null -eq $pss -or $pss -le 0) {
        throw "Unable to parse TOTAL PSS at cycle $i; raw private system output is not saved."
    }
    $nativePss = Get-MemoryNumber -Source $meminfo -Pattern '(?im)^\s*Native Heap\s+([\d,]+)\s+'
    $samples += [ordered]@{
        cycle = $i
        capturedUtc = (Get-Date).ToUniversalTime().ToString('o')
        processId = [int]::Parse($pidText)
        totalPssKiB = $pss
        nativeHeapPssKiB = $nativePss
        manualPlaybackVerified = $false
        manualReconnectVerified = $false
    }
    Write-Host ("Cycle {0}: total PSS={1} KiB; native heap PSS={2} KiB" -f $i, $pss, $nativePss)
}
if ($samples.Count -eq 0) { throw 'No samples collected. No report was written.' }
$firstPss = [long]$samples[0].totalPssKiB
$lastPss = [long]$samples[$samples.Count-1].totalPssKiB
$peakPss = [long](($samples | Measure-Object -Property totalPssKiB -Maximum).Maximum)
$uniquePids = @($samples | Select-Object -ExpandProperty processId -Unique)
$report = [ordered]@{
    schemaVersion = 1
    status = 'observations-only'
    productionQualified = $false
    realAccountReconnectVerified = $false
    hardwarePlaybackVerified = $false
    nativeEnginePlaybackVerified = $false
    scenario = $Scenario
    requestedCycles = $Cycles
    capturedCycles = $samples.Count
    aborted = $aborted
    secondsBetweenActions = $SampleSeconds
    startedUtc = $startUtc
    endedUtc = (Get-Date).ToUniversalTime().ToString('o')
    packageName = $packageName
    candidateApkSha256 = $candidateApkSha256
    sourceCommitSha = if ([string]::IsNullOrWhiteSpace($SourceCommit)) { $null } else { $SourceCommit.ToLowerInvariant() }
    installedApkMatchesCandidate = $false
    device = [ordered]@{ manufacturer=$manufacturer; model=$model; androidVersion=$android; abi=$abi }
    memory = [ordered]@{
        firstTotalPssKiB = $firstPss
        lastTotalPssKiB = $lastPss
        peakTotalPssKiB = $peakPss
        lastMinusFirstPssKiB = ($lastPss - $firstPss)
        distinctProcessIds = $uniquePids.Count
    }
    samples = $samples
    notes = 'Operator must separately attest installed APK identity, real account, first frame, seek correctness, decoder and crash-free behavior. No source media or credentials were collected.'
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$destination = Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path "physical-$Scenario-$abi-$stamp.json"
[IO.File]::WriteAllText($destination, ($report | ConvertTo-Json -Depth 8) + [Environment]::NewLine, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Observations written: $destination"
Write-Host "PSS change: $($lastPss - $firstPss) KiB; peak: $peakPss KiB; distinct PIDs: $($uniquePids.Count)"
Write-Host 'No production/device qualification status was changed.'
