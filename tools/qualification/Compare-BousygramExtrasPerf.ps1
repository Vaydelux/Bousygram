#requires -Version 5.1
<#
.SYNOPSIS
Compare two explicitly captured Extras memory snapshots on the same Android ABI.
.DESCRIPTION
Observational only: does not make a performance pass/fail claim, automate
scrolling, infer image-cache use, or collect account/device identifiers.
Capture both runs manually with Capture-BousygramExtrasPerf.ps1 and the same
device/build, ideally using identical wait times and sample counts.
.EXAMPLE
.\tools\qualification\Compare-BousygramExtrasPerf.ps1 -Baseline '<exact-baseline-file>.json' -LongSession '<exact-long-session-file>.json'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Baseline,
    [Parameter(Mandatory=$true)][string]$LongSession
)
$ErrorActionPreference = 'Stop'
function Read-Evidence {
    param([string]$Path)
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    if ($item.PSIsContainer) { throw 'Expected a JSON evidence file, not a directory.' }
    $evidence = Get-Content -LiteralPath $item.FullName -Raw | ConvertFrom-Json
    if ($evidence.status -ne 'captured-only' -or
        $evidence.productionQualification -ne $false -or
        $evidence.targetScreen -ne 'EXTRAS' -or
        [double]$evidence.averagePssKb -le 0) {
        throw 'Evidence must be an unqualified Extras memory capture with positive PSS.'
    }
    return $evidence
}
$before = Read-Evidence -Path $Baseline
$after = Read-Evidence -Path $LongSession
if ($before.packageName -ne $after.packageName -or
    $before.device.abi -ne $after.device.abi -or
    $before.device.androidVersion -ne $after.device.androidVersion) {
    throw 'Memory snapshots do not use the same app package, Android version, and ABI.'
}
if ($before.measurementPoint -ne 'Baseline' -or
    $after.measurementPoint -ne 'LongSession' -or
    [int]$after.loadedPagesReportedByTester -le [int]$before.loadedPagesReportedByTester) {
    throw 'Compare a Baseline capture to a later LongSession capture with a larger manually reported page count.'
}
$delta = [math]::Round([double]$after.averagePssKb - [double]$before.averagePssKb, 1)
$percent = [math]::Round(100.0 * $delta / [double]$before.averagePssKb, 2)
Write-Host ('Extras PSS comparison (ABI: {0})' -f $before.device.abi)
Write-Host ('Baseline pages: {0}, average PSS: {1} KB' -f $before.loadedPagesReportedByTester, $before.averagePssKb)
Write-Host ('Long-session pages: {0}, average PSS: {1} KB' -f $after.loadedPagesReportedByTester, $after.averagePssKb)
Write-Host ('Observed PSS change: {0} KB ({1}%)' -f $delta, $percent)
Write-Host 'Observation only. Differences include bitmap/GL/native heap, network timing and system noise.'
Write-Host 'Do not infer release-readiness or a causal improvement without repeated A/B device runs.'
