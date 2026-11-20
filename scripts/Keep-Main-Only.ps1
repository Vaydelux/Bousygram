#Requires -Version 5.1
<#
.SYNOPSIS
  Retain only main on Vaydelux/Bousygram's GitHub remote.
.DESCRIPTION
  Dry-run by default. -Execute permanently deletes every remote branch except
  main, including branches that may contain unique/unmerged work. Historical
  commits may eventually be garbage-collected. PR #217/#218 are merged;
  PR #208 is closed and intentionally not merged.
  Requires an authenticated git remote with branch delete permission.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\Keep-Main-Only.ps1
  powershell -ExecutionPolicy Bypass -File .\scripts\Keep-Main-Only.ps1 -Execute
#>
param([switch]$Execute)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$remote = (& git remote get-url origin)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($remote)) {
    throw "Run in a Git checkout with an 'origin' remote."
}
if ($remote -notmatch '^(https://github\.com/|git@github\.com:|ssh://git@github\.com/)(Vaydelux/Bousygram)(\.git)?/?$') {
    throw "Refusing cleanup: origin is not the expected Vaydelux/Bousygram remote."
}

$heads = @(& git ls-remote --heads origin)
if ($LASTEXITCODE -ne 0) {
    throw "Could not enumerate remote branches. Check Git authentication/network."
}
$branches = @(
    $heads | ForEach-Object {
        if ($_ -match '^[0-9a-f]{40}\s+refs/heads/(.+)$') {
            $Matches[1]
        }
    } | Sort-Object -Unique
)
if ($branches -notcontains "main") {
    throw "Main is absent from the remote! Aborting."
}
$delete = @($branches | Where-Object { $_ -ne "main" })
Write-Host "Repository: Vaydelux/Bousygram"
Write-Host "Remote branches: $($branches.Count)"
Write-Host "Branches marked for removal: $($delete.Count)"
foreach ($name in $delete) {
    Write-Host "  $name"
}

if (-not $Execute) {
    Write-Warning "Dry run only. Review branches above; some may contain unmerged commits."
    Write-Host "To delete, rerun this script with -Execute."
    exit 0
}

foreach ($name in $delete) {
    # Fully-qualified deletion ref avoids interpreting branch names as flags.
    $deleteRefspec = ":refs/heads/$name"
    & git push origin $deleteRefspec
    if ($LASTEXITCODE -ne 0) {
        throw "Delete failed for '$name'. Stop and review permissions or protection."
    }
}

$remaining = @(
    & git ls-remote --heads origin | ForEach-Object {
        if ($_ -match '^[0-9a-f]{40}\s+refs/heads/(.+)$') {
            $Matches[1]
        }
    }
)
if ($LASTEXITCODE -ne 0) {
    throw "Cleanup finished, but remote verification failed."
}
if ($remaining.Count -ne 1 -or $remaining[0] -ne "main") {
    throw "Remote verification found additional branches: $($remaining -join ', ')"
}
Write-Host "Verified: main is the only remote branch."
