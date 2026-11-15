#requires -Version 5.1
<#
Parse only the aggregate size from "run-as PACKAGE du -sk files/tdlib_files".
No directory listings, media filenames or paths are persisted.
#>
function Read-BousygramPrivateDuKiB {
    [CmdletBinding()]
    param([AllowEmptyString()][string] $DuText)

    foreach ($line in ($DuText -split '\r?\n')) {
        $match = [regex]::Match($line, '^\s*([0-9]+)\s+(\S+)\s*$')
        if (-not $match.Success) { continue }
        $target = $match.Groups[2].Value
        if ($target -ne 'files/tdlib_files' -and
            -not $target.EndsWith('/files/tdlib_files', [StringComparison]::Ordinal)) {
            continue
        }
        [long] $bytesKiB = 0
        if (-not [long]::TryParse($match.Groups[1].Value, [ref]$bytesKiB)) { continue }
        if ($bytesKiB -lt 0) { continue }
        return $bytesKiB
    }
    return $null
}
