@echo off
setlocal EnableDelayedExpansion
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting Administrator so Windows Firewall can allow phones on Wi-Fi...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

set "JAVA_EXE="
if exist "%LOCALAPPDATA%\TM Mission Control\java-exe.txt" (
  set /p JAVA_EXE=<"%LOCALAPPDATA%\TM Mission Control\java-exe.txt"
)
if not defined JAVA_EXE if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=C:\Program Files\Amazon Corretto\jdk21.0.9_10\bin\java.exe"

netsh advfirewall firewall delete rule name="TM Companion" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion HTTPS" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion HTTPS probe" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion 8080" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion LAN" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion Java" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion Java UDP" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Companion mDNS" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control HTTPS" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control HTTPS probe" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control 8080" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control LAN" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control Java" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control Java UDP" >nul 2>nul
netsh advfirewall firewall delete rule name="TM Mission Control mDNS" >nul 2>nul
netsh advfirewall firewall add rule name="TM Mission Control" dir=in action=allow protocol=TCP localport=443,8080,8765 profile=any enable=yes
netsh advfirewall firewall add rule name="TM Mission Control HTTPS" dir=in action=allow protocol=TCP localport=443 profile=any enable=yes
netsh advfirewall firewall add rule name="TM Mission Control mDNS" dir=in action=allow protocol=UDP localport=5353 profile=any enable=yes
if exist "%JAVA_EXE%" (
  netsh advfirewall firewall add rule name="TM Mission Control Java" dir=in action=allow program="%JAVA_EXE%" protocol=TCP profile=any enable=yes
  netsh advfirewall firewall add rule name="TM Mission Control Java UDP" dir=in action=allow program="%JAVA_EXE%" protocol=UDP profile=any enable=yes
)
if errorlevel 1 (
  echo Failed to add the firewall rule.
  pause
  exit /b 1
)

echo.
echo Allowed Java inbound, TCP 443/8080/8765, and mDNS UDP 5353.
echo Start TM Mission Control, then on the phone open the https:// name it prints.
echo Do not use a raw IP — Chrome will retry forever.
echo.
pause
