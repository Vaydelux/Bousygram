#requires -Version 5.1
# Offline-only synthetic JSON fixtures. They are NOT measurements or release evidence.
$ErrorActionPreference = 'Stop'
$dir = Join-Path ([IO.Path]::GetTempPath()) ("bousygram-perf-test-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $dir | Out-Null
try {
    function MakeFixture([int] $pss, [string] $abi) {
        return [ordered]@{
            status = 'observations-only'
            productionQualified = $false
            hardwarePlaybackVerified = $false
            packageName = 'com.vaydelux.bousygram'
            sampleDurationRequestedSeconds = 60
            device = [ordered]@{ abi=$abi; androidVersion='15'; manufacturer='synthetic'; model='fixture' }
            applicationMemory = [ordered]@{ initialTotalPssKiB=50000; finalTotalPssKiB=$pss }
            composeUiFrames = [ordered]@{ jankyPercent = 5 }
        }
    }
    $a = Join-Path $dir 'synthetic-baseline.json'
    $b = Join-Path $dir 'synthetic-long.json'
    $bad = Join-Path $dir 'synthetic-wrong-abi.json'
    (MakeFixture 52000 'armeabi-v7a' | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $a
    (MakeFixture 62000 'armeabi-v7a' | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $b
    (MakeFixture 62000 'arm64-v8a' | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $bad
    $compare = Join-Path $PSScriptRoot '..\qualification\Compare-BousygramPlaybackPerf.ps1'
    $result = (& $compare -Baseline $a -LongSession $b 6>&1 | Out-String)
    if ($result -notmatch 'Observed final PSS delta:\s*10000 KiB') {
        throw 'Synthetic comparator delta was incorrect.'
    }
    $rejected = $false
    try { & $compare -Baseline $a -LongSession $bad *> $null }
    catch { $rejected = $true }
    if (-not $rejected) { throw 'Synthetic ABI mismatch must be rejected.' }
    Write-Host 'Synthetic-only comparator regression passed. No device qualification inferred.'
} finally {
    Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
}
