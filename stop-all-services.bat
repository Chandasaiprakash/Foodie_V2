@echo off
echo Stopping all microservices...

taskkill /F /IM java.exe

echo All services stopped.
pause
