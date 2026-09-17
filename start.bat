@echo off
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven is not on PATH.
  pause
  exit /b 1
)
if not exist "target\tm-companion-1.0.0.jar" (
  echo Building TM Companion...
  call mvn -q package
  if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
  )
)
java -jar "target\tm-companion-1.0.0.jar" %*
