@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=8.10.2
set GRADLE_HOME=%APP_HOME%.gradle-dist\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Gradle distribution is not installed. Use GitHub Actions or install Gradle locally.
  exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
