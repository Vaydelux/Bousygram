param(
    [string]$TdlibRef = "master",
    [string]$AndroidNdk = "",
    [string]$AndroidPlatform = "android-23",
    [string]$WorkRoot = ""
)

$ErrorActionPreference = "Stop"

function Require-Tool([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required tool '$name' is not installed or not in PATH."
    }
}

Require-Tool "git"
Require-Tool "cmake"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\")
if ([string]::IsNullOrWhiteSpace($WorkRoot)) {
    $WorkRoot = Join-Path $repoRoot "build-tools\tdlib-arm64"
}

if ([string]::IsNullOrWhiteSpace($AndroidNdk)) {
    if ($env:ANDROID_NDK_HOME) {
        $AndroidNdk = $env:ANDROID_NDK_HOME
    } elseif ($env:ANDROID_NDK_ROOT) {
        $AndroidNdk = $env:ANDROID_NDK_ROOT
    } elseif ($env:ANDROID_HOME) {
        $AndroidNdk = Join-Path $env:ANDROID_HOME "ndk\27.0.12077973"
    } elseif ($env:ANDROID_SDK_ROOT) {
        $AndroidNdk = Join-Path $env:ANDROID_SDK_ROOT "ndk\27.0.12077973"
    }
}

if ([string]::IsNullOrWhiteSpace($AndroidNdk) -or -not (Test-Path $AndroidNdk)) {
    throw "Android NDK path is required. Pass -AndroidNdk or set ANDROID_NDK_HOME."
}

$toolchainFile = Join-Path $AndroidNdk "build\cmake\android.toolchain.cmake"
if (-not (Test-Path $toolchainFile)) {
    throw "NDK toolchain file not found at '$toolchainFile'."
}

$srcDir = Join-Path $WorkRoot "src\td"
$buildDir = Join-Path $WorkRoot "build\arm64-v8a"
$outputDir = Join-Path $repoRoot "app\src\main\jniLibs\arm64-v8a"

New-Item -ItemType Directory -Force -Path $WorkRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $WorkRoot "src") | Out-Null
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

if (-not (Test-Path $srcDir)) {
    git clone https://github.com/tdlib/td.git $srcDir
}

Push-Location $srcDir
try {
    git fetch --all --tags
    git checkout $TdlibRef
} finally {
    Pop-Location
}

$configureArgs = @(
    "-G", "Ninja",
    "-S", $srcDir,
    "-B", $buildDir,
    "-DCMAKE_BUILD_TYPE=Release",
    "-DCMAKE_TOOLCHAIN_FILE=$toolchainFile",
    "-DANDROID_ABI=arm64-v8a",
    "-DANDROID_PLATFORM=$AndroidPlatform",
    "-DTD_ENABLE_JNI=ON",
    "-DTD_ENABLE_DOTNET=OFF",
    "-DTD_ENABLE_JAVA=OFF",
    "-DTD_ENABLE_TESTS=OFF"
)

cmake @configureArgs
cmake --build $buildDir --config Release --target tdjni --parallel

$libCandidate = Join-Path $buildDir "libtdjni.so"
if (-not (Test-Path $libCandidate)) {
    $found = Get-ChildItem -Path $buildDir -Recurse -Filter "libtdjni.so" | Select-Object -First 1
    if ($found) {
        $libCandidate = $found.FullName
    }
}

if (-not (Test-Path $libCandidate)) {
    throw "Build completed but libtdjni.so was not found under '$buildDir'."
}

$targetLib = Join-Path $outputDir "libtdjni.so"
Copy-Item -Force $libCandidate $targetLib

Write-Host ""
Write-Host "TDLib arm64 build complete."
Write-Host "Copied:"
Write-Host "  $targetLib"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1) .\gradlew :app:verifyTdlibAbiCoverage"
Write-Host "  2) .\gradlew :app:assembleDebug"
