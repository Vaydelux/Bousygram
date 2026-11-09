#requires -Version 5.1
<#
Read only device-wide df -k /data stdout. No paths or filenames are persisted.
Returns $null on malformed/unsupported OEM output, never a made-up zero.
#>
function Read-BousygramDataSpace {
    [CmdletBinding()]
    param([AllowEmptyString()][string] $DfText)

    foreach ($line in ($DfText -split '\r?\n')) {
        $columns = @([regex]::Split($line.Trim(), '\s+'))
        if ($columns.Count -lt 6 -or $columns[-1] -ne '/data') { continue }
        [long] $total = 0
        [long] $used = 0
        [long] $free = 0
        if (-not [long]::TryParse(($columns[-5] -replace ',', ''), [ref]$total)) { continue }
        if (-not [long]::TryParse(($columns[-4] -replace ',', ''), [ref]$used)) { continue }
        if (-not [long]::TryParse(($columns[-3] -replace ',', ''), [ref]$free)) { continue }
        if ($total -le 0 -or $used -lt 0 -or $free -lt 0 -or
            $used -gt $total -or $free -gt $total - $used) { continue }
        return [pscustomobject]@{
            totalKiB = $total
            usedKiB = $used
            freeKiB = $free
        }
    }
    return $null
}
