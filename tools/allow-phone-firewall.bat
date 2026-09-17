@echo off
:: Opens a UAC prompt to allow TCP 8765 from phones on the LAN.
netsh advfirewall firewall delete rule name="TM Companion" >nul 2>nul
netsh advfirewall firewall add rule name="TM Companion" dir=in action=allow protocol=TCP localport=8765 profile=any enable=yes
if errorlevel 1 (
  echo Failed to add the firewall rule. Right-click this file and Run as administrator.
  pause
  exit /b 1
)
echo Allowed TCP port 8765. Retry the phone browser: http://192.168.1.183:8765/
pause
