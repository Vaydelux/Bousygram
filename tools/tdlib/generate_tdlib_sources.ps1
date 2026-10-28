param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$TdlibRef,
    [string]$CMake = "",
    [string]$Ninja = ""
)
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$srcDir = Join-Path $repoRoot "build-tools\tdlib\src\td"
$exampleDir = Join-Path $srcDir "example\android"
$buildDir = Join-Path $repoRoot "build-tools\tdlib\host-java-generation"
$javaFile = Join-Path $exampleDir "org\drinkless\tdlib\TdApi.java"
if ([string]::IsNullOrWhiteSpace($CMake)) { $CMake = (Get-Command cmake -ErrorAction SilentlyContinue).Source }
if ([string]::IsNullOrWhiteSpace($Ninja)) { $Ninja = (Get-Command ninja -ErrorAction SilentlyContinue).Source }
if (-not $CMake -or -not (Test-Path $CMake)) { throw "cmake is required." }
if (-not $Ninja -or -not (Test-Path $Ninja)) { throw "ninja is required." }
New-Item -ItemType Directory -Force -Path (Split-Path $srcDir) | Out-Null
if (-not (Test-Path (Join-Path $srcDir ".git"))) { git clone https://github.com/tdlib/td.git $srcDir }
git -C $srcDir fetch --tags origin $TdlibRef
git -C $srcDir checkout --detach $TdlibRef
$resolved = (git -C $srcDir rev-parse HEAD).Trim().ToLowerInvariant()
if ($resolved -ne $TdlibRef.ToLowerInvariant()) { throw "TDLib resolved to $resolved, expected $TdlibRef." }
if (Test-Path $buildDir) { Remove-Item -LiteralPath $buildDir -Recurse -Force }
$srcCMake = $exampleDir.Replace('\','/')
$outBuild = $buildDir.Replace('\','/')
& $CMake -G Ninja -S $srcCMake -B $outBuild "-DCMAKE_MAKE_PROGRAM=$($Ninja.Replace('\','/'))" -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON -DTD_ANDROID_JSON=OFF -DTD_ANDROID_JSON_JAVA=OFF
if ($LASTEXITCODE -ne 0) { throw "TDLib Java-generation configure failed." }
& $CMake --build $outBuild --target tl_generate_java --parallel
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $javaFile)) { throw "tl_generate_java failed or did not produce TdApi.java." }
Set-Content -LiteralPath (Join-Path $repoRoot "build-tools\tdlib\RESOLVED_COMMIT.txt") -Value $resolved -NoNewline
Write-Host "Generated $javaFile from $resolved via tl_generate_java"
