@echo off
setlocal
cd /d "%~dp0"
if not exist "target\tm-companion-1.0.0.jar" (
  echo Building TM Companion...
  mvn -q package -DskipTests
  if errorlevel 1 (
    echo Build failed. Install Java 21 and Maven, then run this again.
    pause
    exit /b 1
  )
)
java -jar "%~dp0target\tm-companion-1.0.0.jar" %*
