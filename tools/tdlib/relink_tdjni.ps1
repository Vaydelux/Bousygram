param(
    [Parameter(Mandatory = $true)][string]$BuildDir,
    [Parameter(Mandatory = $true)][string]$Abi,
    [Parameter(Mandatory = $true)][string]$AndroidNdk,
    [Parameter(Mandatory = $true)][string]$OpenSslRoot,
    [Parameter(Mandatory = $true)][string]$OutputDir
)
$ErrorActionPreference = "Stop"
$llvm = Join-Path $AndroidNdk "toolchains\llvm\prebuilt\windows-x86_64\bin"
$clangxx = Join-Path $llvm "clang++.exe"
$strip = Join-Path $llvm "llvm-strip.exe"
$sysroot = (Join-Path $AndroidNdk "toolchains\llvm\prebuilt\windows-x86_64\sysroot") -replace '\\', '/'
$opensslRootUnix = ($OpenSslRoot -replace '\\', '/')

$tdjniObj = Get-ChildItem (Join-Path $BuildDir "CMakeFiles\tdjni.dir") -Recurse -Filter "td_jni.cpp.o" | Select-Object -First 1
if (-not $tdjniObj) { throw "td_jni.cpp.o not found under $BuildDir" }

switch ($Abi) {
    "arm64-v8a" {
        $target = "aarch64-none-linux-android23"
        $tripleLib = "aarch64-linux-android"
        $extra = @()
    }
    "armeabi-v7a" {
        $target = "armv7-none-linux-androideabi23"
        $tripleLib = "arm-linux-androideabi"
        $extra = @("-march=armv7-a", "-mthumb")
    }
    default { throw "Unsupported ABI for relink: $Abi" }
}

Push-Location $BuildDir
try {
    $objs = @(
        ($tdjniObj.FullName -replace '\\', '/'),
        "td/libtdclient.a",
        "td/libtdcore.a",
        "td/libtdapi.a",
        "td/tddb/libtddb.a",
        "td/sqlite/libtdsqlite.a",
        "td/tde2e/libtde2e.a",
        "td/libtdmtproto.a",
        "td/tdnet/libtdnet.a",
        "$opensslRootUnix/$Abi/lib/libssl.a",
        "td/tdactor/libtdactor.a",
        "td/tdutils/libtdutils.a",
        "$opensslRootUnix/$Abi/lib/libcrypto.a"
    )
    $args = @(
        "--target=$target",
        "--sysroot=$sysroot"
    ) + $extra + @(
        "-fPIC", "-O3", "-DNDEBUG", "-shared", "-static-libstdc++",
        "-Wl,--build-id=sha1", "-Wl,--no-rosegment", "-Wl,--no-undefined-version",
        "-Wl,--fatal-warnings", "-Wl,--no-undefined", "-Wl,--gc-sections",
        "-Wl,--exclude-libs,ALL", "-Wl,--icf=safe",
        "-Wl,-soname,libtdjni.so",
        "-o", "libtdjni.so"
    ) + $objs + @(
        "-pthread", "-llog", "-ldl",
        "$sysroot/usr/lib/$tripleLib/23/libz.so",
        "-latomic", "-lm"
    )
    & $clangxx @args
    if ($LASTEXITCODE -ne 0) { throw "Android-only relink failed for $Abi (exit $LASTEXITCODE)" }
    Copy-Item -Force libtdjni.so libtdjni.so.debug
    & $strip --strip-debug --strip-unneeded libtdjni.so.debug -o libtdjni.so
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    Copy-Item -Force libtdjni.so (Join-Path $OutputDir "libtdjni.so")
    Write-Host "Relinked and installed libtdjni.so for $Abi"
} finally {
    Pop-Location
}
