@echo off
rem Builds the debug APK for Xiaomi Editor using the tools in Desktop\AndroidBuildTools.
set "T=%~dp0..\AndroidBuildTools\"
set "JAVA_HOME=%T%jdk-17"
set "GRADLE_USER_HOME=%T%gradle-home"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
cd /d "%~dp0"
call "%T%gradle-8.9\bin\gradle.bat" assembleDebug --no-daemon %*
echo.
echo APK: %CD%\app\build\outputs\apk\debug\XiaomiEditor.apk
