#requires -Version 5.1
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\qualification\Parse-BousygramDataSpace.ps1')

function Assert-Space {
    param([string] $Text, [long] $Total, [long] $Used, [long] $Free)
    $sample = Read-BousygramDataSpace -DfText $Text
    if ($null -eq $sample -or $sample.totalKiB -ne $Total -or
        $sample.usedKiB -ne $Used -or $sample.freeKiB -ne $Free) {
        throw 'Valid device-wide df fixture parsed incorrectly.'
    }
}
Assert-Space -Text @'
Filesystem     1K-blocks    Used Available Use% Mounted on
/dev/block/dm-3  8000000   3000000   5000000  38% /data
'@ -Total 8000000 -Used 3000000 -Free 5000000
Assert-Space -Text "/dev/block/dm-13 7,000 3,000 4,000 40% /data" -Total 7000 -Used 3000 -Free 4000
Assert-Space -Text @'
Filesystem 1K-blocks Used Available Use% Mounted on
/dev/block/dm-3 65000 10000 55000 16% /data
/dev/block/dm-3 1200 300 900 25% /other
'@ -Total 65000 -Used 10000 -Free 55000

foreach ($invalid in @(
    '',
    'Filesystem 1K-blocks Used Available Use% Mounted on',
    '/dev/block/dm-3 100 150 20 150% /data',
    '/dev/block/dm-3 100 50 unknown 50% /data',
    '/dev/block/dm-3 100 50 50 50% /private',
    '/dev/block/dm-3 -1 0 0 0% /data'
)) {
    if ($null -ne (Read-BousygramDataSpace -DfText $invalid)) {
        throw 'Malformed device storage fixture was accepted.'
    }
}
Write-Host 'Device-wide storage parser fixtures passed (observations only).'
