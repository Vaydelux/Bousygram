#requires -Version 5.1
<#
.SYNOPSIS
Observe aggregate TDLib disk growth or post-exit reclamation on a physical Android TV.
.DESCRIPTION
Supports only a debuggable Bousygram APK on a verified physical ARM TV.
Uses "run-as ... du -sk" for one directory aggregate and "df -k /data".
Never reads Telegram files, lists media filenames, exports logcat, account IDs,
URLs, tokens or device serials. This produces OBSERVATIONS, not qualification.
Run separately for baseline, active playback, and after exit. Phase is
operator-supplied and cannot establish that playback actually happened.
.EXAMPLE
.\tools\qualification\Capture-BousygramStreamOnlyStorage.ps1 -Phase playback -Samples 12 -SampleSeconds 20
.EXAMPLE
.\tools\qualification\Capture-BousygramStreamOnlyStorage.ps1 -Phase after-exit -Samples 12 -SampleSeconds 15
#>
[CmdletBinding()]
param(
    [ValidateSet('baseline', 'playback', 'after-exit')]
    [string] $Phase = 'playback',
    [ValidateRange(2, 120)]
    [int] $Samples = 12,
    [ValidateRange(5, 120)]
    [int] $SampleSeconds = 15,
    [string] $DeviceSerial,
    [ValidatePattern('^[a-fA-F0-9]{40}$')]
    [string] $SourceCommit,
    [string] $OutputDirectory = '.\qualification-evidence'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Parse-BousygramDataSpace.ps1')
. (Join-Path $PSScriptRoot 'Parse-BousygramPrivateDu.ps1')

$packageName = 'com.vaydelux.bousygram'
$adb = (Get-Command adb -ErrorAction Stop).Source
$devices = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
if ($devices.Count -eq 0) { throw 'Connect one authorized physical Android TV with adb devices.' }
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($devices.Count -ne 1) { throw 'More than one device connected; specify -DeviceSerial.' }
    $DeviceSerial = ($devices[0] -split '\s+')[0]
}
if (@($devices | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial }).Count -ne 1) {
    throw 'Selected device is not connected or authorized.'
}
$selection = @('-s', $DeviceSerial)

function Invoke-SelectedAdb {
    param([string[]] $CommandArguments)
    $data = & $adb @selection @CommandArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'ADB could not read the requested aggregate device metric.' }
    return (($data | Out-String).Trim())
}

$abi = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.product.cpu.abi')
if ($abi -notin @('armeabi-v7a', 'arm64-v8a')) {
    throw 'Only physical ARM32/ARM64 Android TVs are part of this qualification.'
}
$emulator = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.kernel.qemu')
if ($emulator -eq '1') { throw 'Emulators cannot establish the physical TV storage release gate.' }
$androidVersion = Invoke-SelectedAdb -CommandArguments @('shell', 'getprop', 'ro.build.version.release')
if ((Invoke-SelectedAdb -CommandArguments @('shell', 'pm', 'path', $packageName)) -notmatch 'package:') {
    throw 'Bousygram is not installed.'
}
Write-Host 'This requires a debuggable Bousygram build with TDLib files already present.'
Write-Host 'Keep the named phase stable during the sample. The phase is not independently verified.'
Write-Host 'No media contents, filenames, account identifiers, raw ADB output or serial are saved.'

$observations = @()
for ($i = 1; $i -le $Samples; $i++) {
    if ($i -gt 1) { Start-Sleep -Seconds $SampleSeconds }
    $duOutput = Invoke-SelectedAdb -CommandArguments @(
        'shell', 'run-as', $packageName, 'du', '-sk', 'files/tdlib_files'
    )
    $tdlibKiB = Read-BousygramPrivateDuKiB -DuText $duOutput
    if ($null -eq $tdlibKiB) {
        throw 'TDLib aggregate was unavailable. Confirm a debug APK, initialized TDLib, and run-as support.'
    }

    $space = $null
    try {
        $dfOutput = Invoke-SelectedAdb -CommandArguments @('shell', 'df', '-k', '/data')
        $space = Read-BousygramDataSpace -DfText $dfOutput
    } catch {
        # Some OEM builds restrict df; null is not evidence of zero free space.
    }

    $observations += [ordered]@{
        sample = $i
        timeUtc = [DateTime]::UtcNow.ToString('o')
        tdLibDirectoryKiB = $tdlibKiB
        deviceDataFreeKiB = if ($null -ne $space) { $space.freeKiB } else { $null }
    }
    Write-Host ("Sample {0}/{1}: TDLib aggregate={2} KiB; /data free={3} KiB" -f
        $i, $Samples, $tdlibKiB, $(if ($null -ne $space) { $space.freeKiB } else { 'unavailable' }))
}

$first = [long]$observations[0].tdLibDirectoryKiB
$last = [long]$observations[$observations.Count - 1].tdLibDirectoryKiB
$peak = [long](($observations | Measure-Object -Property tdLibDirectoryKiB -Maximum).Maximum)
$report = [ordered]@{
    schemaVersion = 1
    status = 'observations-only'
    productionQualified = $false
    hardwarePlaybackVerified = $false
    phaseClaim = $Phase
    sourceCommitClaim = if ([string]::IsNullOrWhiteSpace($SourceCommit)) { $null } else { $SourceCommit.ToLowerInvariant() }
    device = [ordered]@{
        abi = $abi
        androidVersion = $androidVersion
        physicalDeviceCheck = 'qemu-property'
    }
    sampling = [ordered]@{
        count = $Samples
        intervalSeconds = $SampleSeconds
        firstTdLibKiB = $first
        lastTdLibKiB = $last
        peakTdLibKiB = $peak
        deltaTdLibKiB = $last - $first
    }
    notes = @(
        'du -sk records allocated blocks for the complete TDLib directory, including databases and other cached files.',
        'System /data usage includes other apps; free-space changes cannot be attributed exclusively to Bousygram.',
        'Comparisons need the same device, test media, account/cache state and debug build.',
        'A phase claim does not prove playback occurred. This report is not production qualification.'
    )
    samples = $observations
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$filename = "stream-only-$Phase-$abi-$stamp.json"
$destination = Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path $filename
$report | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $destination -Encoding UTF8
Write-Host "Saved privacy-minimized aggregate metrics: $destination"
