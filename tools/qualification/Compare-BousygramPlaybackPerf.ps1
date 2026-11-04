#requires -Version 5.1
<#
.SYNOPSIS
Compare two actual, manually captured Bousygram playback diagnostic JSON records.
.DESCRIPTION
Observations only: app UI gfxinfo is not video-decoder telemetry and JSON files
do not prove that the same signed APK ran in both sessions. Use the same APK,
clip, duration, device and network for meaningful comparisons.
.EXAMPLE
.\tools\qualification\Compare-BousygramPlaybackPerf.ps1 -Baseline '.\before.json' -LongSession '.\after.json'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string] $Baseline,
    [Parameter(Mandatory=$true)][string] $LongSession
)
$ErrorActionPreference = "Stop"
function Read-Evidence {
    param([string] $Path)
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    if ($item.PSIsContainer) { throw "Expected a JSON capture file." }
    $json = Get-Content -LiteralPath $item.FullName -Raw | ConvertFrom-Json
    if ($json.status -ne 'observations-only' -or
        $json.productionQualified -ne $false -or
        $json.hardwarePlaybackVerified -ne $false -or
        $json.packageName -ne 'com.vaydelux.bousygram' -or
        [double]$json.applicationMemory.initialTotalPssKiB -le 0 -or
        [double]$json.applicationMemory.finalTotalPssKiB -le 0 -or
        [int]$json.sampleDurationRequestedSeconds -le 0) {
        throw "Invalid or unqualified playback performance evidence."
    }
    return $json
}
$before = Read-Evidence -Path $Baseline
$after = Read-Evidence -Path $LongSession
if ($before.device.abi -ne $after.device.abi -or
    $before.device.androidVersion -ne $after.device.androidVersion -or
    $before.device.manufacturer -ne $after.device.manufacturer -or
    $before.device.model -ne $after.device.model -or
    $before.sampleDurationRequestedSeconds -ne $after.sampleDurationRequestedSeconds) {
    throw "Captures must use matching device, ABI, Android version and sample duration."
}
$delta = [double]$after.applicationMemory.finalTotalPssKiB - [double]$before.applicationMemory.finalTotalPssKiB
$percent = [math]::Round(100.0 * $delta / [double]$before.applicationMemory.finalTotalPssKiB, 2)
Write-Host ('Playback observations: {0} / {1}' -f $before.device.model, $before.device.abi)
Write-Host ('Observed final PSS delta: {0} KiB ({1}%)' -f $delta, $percent)
if ($null -ne $before.composeUiFrames.jankyPercent -and
    $null -ne $after.composeUiFrames.jankyPercent) {
    $jankDelta = [math]::Round(
        [double]$after.composeUiFrames.jankyPercent - [double]$before.composeUiFrames.jankyPercent, 2
    )
    Write-Host ('UI gfxinfo jank percentage-point delta: {0}' -f $jankDelta)
}
Write-Host "Observations only: no video-decoder or first-frame measurement is inferred."
Write-Host "Confirm identical APK and clip manually, and repeat runs before any performance claim."
