<#
.SYNOPSIS
    Installs the optimal Bousygram APK onto an Android TV or Android device over ADB.

.DESCRIPTION
    Connects to the specified TV over ADB, queries the device ABI list and SDK version,
    selects the most compatible APK from the dist/ folder, validates disk space, and installs.

.PARAMETER TvIp
    The IP address of the target Android TV (e.g. 192.168.1.50).

.PARAMETER Port
    ADB network port. Default is 5555.

.PARAMETER Variant
    Build variant to install: "release" (default) or "debug".
#>

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$TvIp,

    [Parameter(Mandatory = $false)]
    [int]$Port = 5555,

    [Parameter(Mandatory = $false)]
    [ValidateSet("release", "debug")]
    [string]$Variant = "release"
)

$ErrorActionPreference = "Stop"

# Ensure adb is accessible
$adbCmd = (Get-Command adb -ErrorAction SilentlyContinue)?.Source
if (-not $adbCmd) {
    if ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe")) {
        $adbCmd = "$env:ANDROID_HOME\platform-tools\adb.exe"
    } elseif ($env:ANDROID_SDK_ROOT -and (Test-Path "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe")) {
        $adbCmd = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    } else {
        Write-Error "adb command not found in PATH or ANDROID_HOME. Please ensure Android SDK platform-tools is installed."
        exit 1
    }
}

$target = "${TvIp}:${Port}"
Write-Host "Connecting to Android TV at $target..." -ForegroundColor Cyan
& $adbCmd connect $target

# Verify connection
$devices = & $adbCmd devices
if ($devices -notmatch [regex]::Escape($target)) {
    Write-Error "Failed to connect to device at $target. Please verify TV IP and ensure 'ADB debugging' is enabled in Developer Options."
    exit 1
}

Write-Host "Connected successfully to $target." -ForegroundColor Green

# Query Device Properties
Write-Host "Querying device properties..." -ForegroundColor Cyan
$abiListRaw = (& $adbCmd -s $target shell getprop ro.product.cpu.abilist).Trim()
if (-not $abiListRaw) {
    # Fallback to single abi property if abilist is empty
    $abiListRaw = (& $adbCmd -s $target shell getprop ro.product.cpu.abi).Trim()
}
$sdkVersionRaw = (& $adbCmd -s $target shell getprop ro.build.version.sdk).Trim()
$deviceModel = (& $adbCmd -s $target shell getprop ro.product.model).Trim()

Write-Host "Device Model : $deviceModel"
Write-Host "Supported ABIs: $abiListRaw"
Write-Host "Android SDK   : $sdkVersionRaw"

$deviceAbis = $abiListRaw -split '[\s,]+' | Where-Object { $_ -ne "" }

# Check SDK version compatibility (minSdk is 26)
[int]$deviceSdk = 0
if ([int]::TryParse($sdkVersionRaw, [ref]$deviceSdk)) {
    if ($deviceSdk -lt 26) {
        Write-Warning "Device SDK ($deviceSdk) is lower than Bousygram's minSdkVersion (26 / Android 8.0)."
    }
}

# Locate dist directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$distDir = Join-Path $projectRoot "dist"

if (-not (Test-Path $distDir)) {
    Write-Error "dist/ directory not found at $distDir. Please run Gradle build and copyApksToDist first."
    exit 1
}

# Pick best matching APK
# arm64-v8a if supported, otherwise armeabi-v7a, otherwise universal
$selectedApk = $null
$selectedAbi = ""

$arm64Apk = Get-ChildItem -Path $distDir -Filter "*-$Variant-arm64-v8a.apk" | Select-Object -First 1
$arm32Apk = Get-ChildItem -Path $distDir -Filter "*-$Variant-armeabi-v7a.apk" | Select-Object -First 1
$univApk  = Get-ChildItem -Path $distDir -Filter "*-$Variant-universal.apk" | Select-Object -First 1

if ($deviceAbis -contains "arm64-v8a" -and $arm64Apk) {
    $selectedApk = $arm64Apk.FullName
    $selectedAbi = "arm64-v8a"
} elseif ($deviceAbis -contains "armeabi-v7a" -and $arm32Apk) {
    $selectedApk = $arm32Apk.FullName
    $selectedAbi = "armeabi-v7a"
} elseif ($univApk) {
    $selectedApk = $univApk.FullName
    $selectedAbi = "universal"
} elseif ($arm32Apk) {
    # 32-bit can run on both 32-bit and 64-bit ARM devices
    $selectedApk = $arm32Apk.FullName
    $selectedAbi = "armeabi-v7a (fallback)"
} elseif ($arm64Apk) {
    $selectedApk = $arm64Apk.FullName
    $selectedAbi = "arm64-v8a (fallback)"
}

if (-not $selectedApk) {
    Write-Error "No matching APK found in $distDir for variant '$Variant'. Check available APK files."
    exit 1
}

$apkFileInfo = Get-Item $selectedApk
$apkSizeBytes = $apkFileInfo.Length
$apkSizeMB = [math]::Round($apkSizeBytes / 1MB, 2)

Write-Host "Selected APK: $($apkFileInfo.Name) ($selectedAbi, $apkSizeMB MB)" -ForegroundColor Green

# Check Device Free Storage
Write-Host "`nChecking device free storage on /data..." -ForegroundColor Cyan
$dfOutput = & $adbCmd -s $target shell df /data
Write-Host ($dfOutput -join "`n")

# Parse free space (in KB or bytes from df output)
# Typical output:
# Filesystem      1K-blocks    Used Available Use% Mounted on
# /data/media       5242880 2097152   3145728  40% /data
$freeBytes = 0
foreach ($line in $dfOutput) {
    if ($line -match '/data') {
        $parts = $line -split '\s+'
        # If columns: Filesystem, 1K-blocks, Used, Available, Use%, Mounted on
        if ($parts.Length -ge 4) {
            $availCol = $parts[3]
            [long]$availKb = 0
            if ([long]::TryParse($availCol, [ref]$availKb)) {
                $freeBytes = $availKb * 1024
            }
        }
    }
}

$requiredStorageBytes = $apkSizeBytes * 3
$requiredStorageMB = [math]::Round($requiredStorageBytes / 1MB, 2)
if ($freeBytes -gt 0) {
    $freeMB = [math]::Round($freeBytes / 1MB, 2)
    Write-Host "Estimated free space: $freeMB MB (Recommended minimum 3x APK size: $requiredStorageMB MB)"
    if ($freeBytes -lt $requiredStorageBytes) {
        Write-Warning "Free disk space on /data ($freeMB MB) is less than 3x APK size ($requiredStorageMB MB)! Installation may fail with INSTALL_FAILED_INSUFFICIENT_STORAGE."
    }
}

# Install Function
function Install-BousygramApk {
    param([string]$apkPath)

    Write-Host "`nInstalling $apkPath onto $target..." -ForegroundColor Cyan
    $installOutput = & $adbCmd -s $target install -r $apkPath 2>&1
    $outputStr = $installOutput -join "`n"
    Write-Host $outputStr

    if ($outputStr -match "Success") {
        Write-Host "`n[SUCCESS] Bousygram successfully installed on $target!" -ForegroundColor Green
        return $true
    }

    # Error analysis
    Write-Host "`n[INSTALLATION FAILED]" -ForegroundColor Red
    Write-Host "Raw output: $outputStr" -ForegroundColor Yellow

    if ($outputStr -match "INSTALL_FAILED_NO_MATCHING_ABIS") {
        Write-Host "Reason: The device CPU architecture does not match the APK's native libraries." -ForegroundColor Red
        Write-Host "Explanation: The TV has a 32-bit-only CPU/OS ($abiListRaw) and cannot run 64-bit ARM binaries." -ForegroundColor Yellow
        Write-Host "Fix: Use the armeabi-v7a or universal APK." -ForegroundColor Cyan
    } elseif ($outputStr -match "INSTALL_FAILED_OLDER_SDK") {
        Write-Host "Reason: The Android version on the TV is older than Bousygram's minSdkVersion." -ForegroundColor Red
        Write-Host "Explanation: Bousygram requires Android 8.0 (API 26) or higher. Device is running SDK $sdkVersionRaw." -ForegroundColor Yellow
    } elseif ($outputStr -match "INSTALL_FAILED_INSUFFICIENT_STORAGE") {
        Write-Host "Reason: Device storage is full." -ForegroundColor Red
        Write-Host "Explanation: Clear app cache or uninstall unused apps on the TV to free up at least $requiredStorageMB MB." -ForegroundColor Yellow
    } elseif ($outputStr -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "Reason: Signature mismatch with previously installed version." -ForegroundColor Red
        Write-Host "Explanation: The app already on the TV was signed with a different key (e.g. debug vs release)." -ForegroundColor Yellow
        
        $confirm = Read-Host "Would you like to uninstall the existing app first? Note: this will reset local settings. (y/N)"
        if ($confirm -eq 'y' -or $confirm -eq 'Y') {
            Write-Host "Uninstalling com.vaydelux.bousygram..." -ForegroundColor Cyan
            & $adbCmd -s $target uninstall com.vaydelux.bousygram
            Write-Host "Retrying installation..." -ForegroundColor Cyan
            $retryOutput = & $adbCmd -s $target install -r $apkPath 2>&1
            Write-Host ($retryOutput -join "`n")
            if ($retryOutput -match "Success") {
                Write-Host "`n[SUCCESS] Bousygram successfully installed after reinstall!" -ForegroundColor Green
                return $true
            }
        } else {
            Write-Host "Installation aborted without uninstalling." -ForegroundColor Yellow
        }
    } else {
        Write-Host "Encountered unexpected ADB error. See raw output above for details." -ForegroundColor Yellow
    }

    return $false
}

$success = Install-BousygramApk -apkPath $selectedApk
if (-not $success) {
    exit 1
}
