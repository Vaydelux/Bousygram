@echo off
setlocal EnableExtensions
set REPO=C:\Users\Jericho.Mosqueda\Downloads\Tele-main--1-
set ANDROID_EXAMPLE=%REPO%\build-tools\tdlib\src\td\example\android
set CMAKE=%REPO%\prerequisites\cmake-4.5.0-rc1-windows-x86_64\bin\cmake.exe
set NINJA=%REPO%\prerequisites\ninja-win
set GPERF=%REPO%\prerequisites\gperf
set PATH=%REPO%\prerequisites\cmake-4.5.0-rc1-windows-x86_64\bin;%NINJA%;%GPERF%;%PATH%

where cl
where cmake
where ninja
where gperf

cd /d "%ANDROID_EXAMPLE%"
if exist build-native-Java rmdir /s /q build-native-Java
mkdir build-native-Java
cd build-native-Java

"%CMAKE%" -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER=cl -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON ..
if errorlevel 1 exit /b 1
"%CMAKE%" --build . --parallel
if errorlevel 1 exit /b 1
echo Native TDLib source generation finished.
exit /b 0
