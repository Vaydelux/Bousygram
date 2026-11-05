#requires -Version 5.1
# Synthetic-only device reports: no real Telegram, hardware, or release approval.
$ErrorActionPreference = 'Stop'
$dir = Join-Path ([IO.Path]::GetTempPath()) ("bousygram-physical-soak-fixtures-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $dir | Out-Null

function MakeFixture {
    param([string] $Abi = 'armeabi-v7a', [int] $IncreasePerCycle = 100,
        [int] $ProcessId = 1234, [int] $DifferentProcessAt = -1,
        [bool] $Interrupted = $false, [bool] $FakeManualPass = $false)
    $rows = @(
        for ($i = 1; $i -le 20; $i++) {
            [ordered]@{
                cycle = $i
                capturedUtc = '2026-11-06T00:00:00Z'
                processId = if ($i -eq $DifferentProcessAt) { 6789 } else { $ProcessId }
                totalPssKiB = (50000 + $i * $IncreasePerCycle)
                nativeHeapPssKiB = 15000
                manualPlaybackVerified = $FakeManualPass
                manualReconnectVerified = $false
            }
        }
    )
    return [ordered]@{
        schemaVersion = 1
        status = 'observations-only'
        productionQualified = $false
        realAccountReconnectVerified = $false
        hardwarePlaybackVerified = $false
        nativeEnginePlaybackVerified = $false
        scenario = 'arm32-playback'
        requestedCycles = 20
        capturedCycles = 20
        aborted = $Interrupted
        secondsBetweenActions = 60
        packageName = 'com.vaydelux.bousygram'
        candidateApkSha256 = ('a' * 64)
        installedApkMatchesCandidate = $false
        device = [ordered]@{
            manufacturer = 'synthetic'
            model = 'fixture'
            androidVersion = '10'
            abi = $Abi
        }
        samples = $rows
    }
}
function WriteFixture {
    param([object] $Object, [string] $Name)
    $path = Join-Path $dir $Name
    $Object | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $path -Encoding UTF8
    return $path
}
function AssertRejected {
    param([string] $Compare, [string] $Base, [string] $Other)
    $failedAsExpected = $false
    try {
        & $Compare -Baseline $Base -Candidate $Other -MinCycles 10 *> $null
    } catch {
        $failedAsExpected = $true
    }
    if (-not $failedAsExpected) { throw "Invalid synthetic report was accepted: $Other" }
}
try {
    $baseline = WriteFixture -Object (MakeFixture) -Name 'baseline.json'
    $candidate = WriteFixture -Object (MakeFixture -IncreasePerCycle 200) -Name 'candidate.json'
    $differentAbi = WriteFixture -Object (MakeFixture -Abi 'arm64-v8a') -Name 'wrong-abi.json'
    $restart = WriteFixture -Object (MakeFixture -DifferentProcessAt 19) -Name 'restarted.json'
    $incomplete = WriteFixture -Object (MakeFixture -Interrupted $true) -Name 'incomplete.json'
    $fakePass = WriteFixture -Object (MakeFixture -FakeManualPass $true) -Name 'fake-pass.json'

    $compare = Join-Path $PSScriptRoot '..\qualification\Compare-BousygramPhysicalSoak.ps1'
    $output = (& $compare -Baseline $baseline -Candidate $candidate -MinCycles 10 -RequireSameApk 6>&1 | Out-String)
    if ($output -notmatch 'Observed candidate-minus-baseline drift:' -or
        $output -notmatch 'OBSERVATIONS ONLY') {
        throw 'Valid fixture comparison did not render the observation-only report.'
    }
    AssertRejected -Compare $compare -Base $baseline -Other $differentAbi
    AssertRejected -Compare $compare -Base $baseline -Other $restart
    AssertRejected -Compare $compare -Base $baseline -Other $incomplete
    AssertRejected -Compare $compare -Base $baseline -Other $fakePass

    Write-Host 'Physical-soak comparator synthetic regression passed (no hardware qualification claimed).'
} finally {
    Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
}
