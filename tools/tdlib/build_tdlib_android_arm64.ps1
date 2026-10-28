param(
    [string]$TdlibRef = "master",
    [string]$AndroidNdk = "",
    [string]$AndroidPlatform = "android-23",
    [string]$WorkRoot = "",
    [string]$OpenSslRoot = "",
    [string[]]$Abis = @("arm64-v8a", "armeabi-v7a")
)
$ErrorActionPreference = "Stop"
function Require-Tool([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "Required tool '$name' is not installed or not in PATH." }
}
Require-Tool "git"
Require-Tool "cmake"
$supportedAbis = @("arm64-v8a", "armeabi-v7a")
if ($Abis.Count -eq 0 -or @($Abis | Where-Object { $_ -notin $supportedAbis }).Count -gt 0) { throw "Abis must be selected from: $($supportedAbis -join ', ')" }
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if ([string]::IsNullOrWhiteSpace($WorkRoot)) { $WorkRoot = Join-Path $repoRoot "build-tools\tdlib" }
if ([string]::IsNullOrWhiteSpace($AndroidNdk)) {
    if ($env:ANDROID_NDK_HOME) { $AndroidNdk = $env:ANDROID_NDK_HOME }
    elseif ($env:ANDROID_NDK_ROOT) { $AndroidNdk = $env:ANDROID_NDK_ROOT }
    elseif ($env:ANDROID_SDK_ROOT) { $AndroidNdk = Join-Path $env:ANDROID_SDK_ROOT "ndk\27.0.12077973" }
    elseif ($env:ANDROID_HOME) { $AndroidNdk = Join-Path $env:ANDROID_HOME "ndk\27.0.12077973" }
}
if ([string]::IsNullOrWhiteSpace($AndroidNdk) -or -not (Test-Path $AndroidNdk)) { throw "Android NDK path is required. Pass -AndroidNdk or set ANDROID_NDK_HOME." }
$opensslRoots = @{}
if ([string]::IsNullOrWhiteSpace($OpenSslRoot)) { throw "Android OpenSSL root is required. Build OpenSSL for each ABI and pass -OpenSslRoot to the common parent directory." }
foreach ($abi in $Abis) {
    $abiRoot = Join-Path $OpenSslRoot $abi
    if (-not (Test-Path (Join-Path $abiRoot "include\openssl\ssl.h")) -or -not (Test-Path (Join-Path $abiRoot "lib\libcrypto.a"))) {
        throw "OpenSSL for $abi is incomplete under '$abiRoot'. Expected include\openssl\ssl.h and lib\libcrypto.a."
    }
    $opensslRoots[$abi] = $abiRoot
}
$toolchainFile = Join-Path $AndroidNdk "build\cmake\android.toolchain.cmake"
if (-not (Test-Path $toolchainFile)) { throw "NDK toolchain file not found at '$toolchainFile'." }
$srcDir = Join-Path $WorkRoot "src\td"
New-Item -ItemType Directory -Force -Path (Join-Path $WorkRoot "src") | Out-Null
if (-not (Test-Path $srcDir)) { git clone https://github.com/tdlib/td.git $srcDir }
Push-Location $srcDir
try { git fetch --all --tags; git checkout $TdlibRef; $resolvedTdlibCommit = (git rev-parse HEAD).Trim() }
finally { Pop-Location }
foreach ($abi in $Abis) {
    $buildDir = Join-Path $WorkRoot "build\$abi"
    $outputDir = Join-Path $repoRoot "app\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Force -Path $buildDir, $outputDir | Out-Null
    # TDLib's Android JNI target is defined by the Android example wrapper,
    # not by the top-level TDLib CMake project.
    cmake -G Ninja -S (Join-Path $srcDir "example\android") -B $buildDir -DCMAKE_BUILD_TYPE=Release `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchainFile" "-DANDROID_ABI=$abi" `
        "-DANDROID_PLATFORM=$AndroidPlatform" -DTD_ENABLE_JNI=ON `
        -DTD_ANDROID_JSON=OFF -DTD_ANDROID_JSON_JAVA=OFF `
        "-DOPENSSL_ROOT_DIR=$($opensslRoots[$abi])"
    cmake --build $buildDir --config Release --target tdjni --parallel
    $libCandidate = Get-ChildItem -Path $buildDir -Recurse -Filter "libtdjni.so" | Select-Object -First 1
    if (-not $libCandidate) { throw "Build completed but libtdjni.so was not found for $abi." }
    Copy-Item -Force $libCandidate.FullName (Join-Path $outputDir "libtdjni.so")
}
$metadata = Join-Path $repoRoot "tools\tdlib\BUILD_METADATA.txt"
@("TDLib commit: $resolvedTdlibCommit", "NDK: $AndroidNdk", "Android platform: $AndroidPlatform", "ABIs: $($Abis -join ', ')", "Generated: $(Get-Date -Format o)") | Set-Content $metadata
Write-Host "TDLib native libraries built for $($Abis -join ', ') from $resolvedTdlibCommit"
Write-Host "Next: .\gradlew.bat :app:verifyTdlibAbiCoverage"
