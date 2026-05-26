@echo off
echo ==========================================
echo  Resetting Kafka
echo ==========================================

:: --- STOP KAFKA (force kill) ---
echo Stopping Kafka...
taskkill /F /IM java.exe >nul 2>&1

:: --- CLEAN KAFKA LOGS ---
echo Cleaning Kafka logs directory...
rmdir /s /q C:\kafka-logs

:: --- START KAFKA BROKER ---
echo Starting Kafka Broker in KRaft mode...
start cmd /k "cd C:\kafka && .\bin\windows\kafka-storage.bat format -t MkU3OEVBNTcwNTJENDM2Qk -c .\config\kraft\server.properties --ignore-formatted && .\bin\windows\kafka-server-start.bat .\config\kraft\server.properties"

echo ==========================================
echo Kafka restarted successfully!
echo ==========================================
pause
