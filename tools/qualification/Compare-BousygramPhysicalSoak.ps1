#requires -Version 5.1
<#
.SYNOPSIS
Compare two sanitized physical-device soak reports without granting release approval.
.DESCRIPTION
Rejects mismatched scenarios/devices, interrupted captures, invalid samples and
process restarts. Reports PSS drift and peak differences as observations, not
as automatic proof of a memory leak or device qualification.
.EXAMPLE
.\tools\qualification\Compare-BousygramPhysicalSoak.ps1 -Baseline .\before.json -Candidate .\after.json -MinCycles 20
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [string] $Baseline,
    [Parameter(Mandatory=$true)]
    [string] $Candidate,
    [ValidateRange(2, 100)]
    [int] $MinCycles = 10,
    [switch] $RequireSameApk
)
$ErrorActionPreference = 'Stop'

function Read-Soak {
    param([string] $Path)
    $source = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    $report = Get-Content -LiteralPath $source -Raw | ConvertFrom-Json
    if ($null -eq $report -or $report.schemaVersion -ne 1 -or $report.status -ne 'observations-only') {
        throw 'Unrecognized soak JSON schema; provide the output of Capture-BousygramPhysicalSoak.ps1.'
    }
    if ($report.productionQualified -ne $false -or $report.realAccountReconnectVerified -ne $false -or
        $report.hardwarePlaybackVerified -ne $false -or $report.nativeEnginePlaybackVerified -ne $false) {
        throw 'The sampler cannot claim manual verification or production qualification.'
    }
    if ($report.aborted -ne $false) { throw 'Capture was interrupted; do not infer stability from incomplete samples.' }
    $samples = @($report.samples)
    if ($samples.Count -lt $MinCycles -or $samples.Count -ne [int]$report.capturedCycles -or
        $samples.Count -ne [int]$report.requestedCycles) {
        throw 'Sample count does not match a complete requested soak run.'
    }
    if ($null -eq $report.device -or
        $report.device.abi -notin @('armeabi-v7a', 'arm64-v8a') -or
        [string]::IsNullOrWhiteSpace($report.device.model) -or
        [string]::IsNullOrWhiteSpace($report.device.androidVersion)) {
        throw 'Invalid device identity in soak report.'
    }
    if ($report.scenario -notin @('tdlib-reconnect','arm32-playback','native-engines')) {
        throw 'Unrecognized physical-device scenario.'
    }
    $pidValues = @()
    for ($i=0; $i -lt $samples.Count; $i++) {
        $sample = $samples[$i]
        if ([int]$sample.cycle -ne ($i + 1) -or
            $null -eq $sample.totalPssKiB -or [long]$sample.totalPssKiB -le 0 -or
            $null -eq $sample.processId -or [long]$sample.processId -le 0 -or
            $sample.manualPlaybackVerified -ne $false -or $sample.manualReconnectVerified -ne $false) {
            throw 'Invalid sample sequence, PSS, process ID, or manual verification flags.'
        }
        $pidValues += [long]$sample.processId
    }
    if (@($pidValues | Select-Object -Unique).Count -ne 1) {
        throw 'Process ID changed within the run; investigate a crash/restart before comparing memory.'
    }
    return $report
}

function Get-MedianPss {
    param([object[]] $Samples, [int] $Start, [int] $Length)
    $sorted = @($Samples[$Start..($Start + $Length - 1)] |
        ForEach-Object { [double]$_.totalPssKiB } | Sort-Object)
    if (($Length % 2) -eq 1) { return [double]$sorted[[int][math]::Floor($Length / 2)] }
    return ([double]$sorted[($Length / 2) - 1] + [double]$sorted[($Length / 2)]) / 2.0
}

$before = Read-Soak -Path $Baseline
$after = Read-Soak -Path $Candidate
if ($before.scenario -ne $after.scenario -or
    $before.device.abi -ne $after.device.abi -or
    $before.device.model -ne $after.device.model -or
    $before.device.manufacturer -ne $after.device.manufacturer -or
    $before.device.androidVersion -ne $after.device.androidVersion) {
    throw 'Baseline and candidate must use the same scenario, ABI, device model, manufacturer, and Android version.'
}
if ([int]$before.secondsBetweenActions -ne [int]$after.secondsBetweenActions -or
    [int]$before.capturedCycles -ne [int]$after.capturedCycles) {
    throw 'Different capture durations or cycle counts are not a controlled comparison.'
}
if ($RequireSameApk -and
    ([string]::IsNullOrWhiteSpace($before.candidateApkSha256) -or
    [string]::IsNullOrWhiteSpace($after.candidateApkSha256) -or
    $before.candidateApkSha256 -ne $after.candidateApkSha256)) {
    throw 'RequireSameApk requested but the candidate APK hashes are missing or differ.'
}
$beforeSamples = @($before.samples)
$afterSamples = @($after.samples)
$window = [math]::Max(2, [int][math]::Floor($afterSamples.Count / 5))
$baselineFirst = Get-MedianPss -Samples $beforeSamples -Start 0 -Length $window
$baselineLast = Get-MedianPss -Samples $beforeSamples -Start ($beforeSamples.Count - $window) -Length $window
$candidateFirst = Get-MedianPss -Samples $afterSamples -Start 0 -Length $window
$candidateLast = Get-MedianPss -Samples $afterSamples -Start ($afterSamples.Count - $window) -Length $window
$baselinePeak = [long](($beforeSamples | Measure-Object -Property totalPssKiB -Maximum).Maximum)
$candidatePeak = [long](($afterSamples | Measure-Object -Property totalPssKiB -Maximum).Maximum)
$baselineDrift = $baselineLast - $baselineFirst
$candidateDrift = $candidateLast - $candidateFirst
$peakDelta = $candidatePeak - $baselinePeak

Write-Host "Scenario: $($after.scenario) | $($after.device.abi) | $($after.device.manufacturer) $($after.device.model)"
Write-Host "Matched cycles: $($afterSamples.Count) | median window: $window samples"
Write-Host ("Baseline early/late PSS median: {0:N1} / {1:N1} KiB (drift {2:N1} KiB)" -f $baselineFirst, $baselineLast, $baselineDrift)
Write-Host ("Candidate early/late PSS median: {0:N1} / {1:N1} KiB (drift {2:N1} KiB)" -f $candidateFirst, $candidateLast, $candidateDrift)
Write-Host ("Observed candidate-minus-baseline peak PSS: {0:N1} KiB" -f $peakDelta)
Write-Host ("Observed candidate-minus-baseline drift: {0:N1} KiB" -f ($candidateDrift - $baselineDrift))
Write-Host 'Result: OBSERVATIONS ONLY. Memory drift is not proof of a leak, and the report does not certify real account/decoder correctness.'
