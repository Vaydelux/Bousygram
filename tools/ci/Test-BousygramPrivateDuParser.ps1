#requires -Version 5.1
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\qualification\Parse-BousygramPrivateDu.ps1')

foreach ($example in @(
    @{ Text = "4096    files/tdlib_files"; Want = 4096L },
    @{ Text = "0`tfiles/tdlib_files"; Want = 0L },
    @{ Text = "32`t/data/user/0/app/files/tdlib_files"; Want = 32L },
    @{ Text = "8192`t./files/tdlib_files"; Want = $null }
)) {
    $actual = Read-BousygramPrivateDuKiB -DuText $example.Text
    if ($actual -ne $example.Want) {
        throw 'Aggregate private du parser did not match a safe fixture.'
    }
}
foreach ($invalid in @(
    '',
    'du: files/tdlib_files: No such file or directory',
    '123 files/other',
    '123 files/tdlib_files/one-video.mp4',
    '-22 files/tdlib_files',
    '123.5 files/tdlib_files',
    '999999999999999999999999 files/tdlib_files'
)) {
    if ($null -ne (Read-BousygramPrivateDuKiB -DuText $invalid)) {
        throw 'Malformed or nonaggregate private du input was accepted.'
    }
}
Write-Host 'Private TDLib aggregate disk sampler parser fixtures passed.'
