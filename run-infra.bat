@echo off
echo Starting Infrastructure Services...

:: Start MySQL
net start MySQL80

:: Start MongoDB
net start MongoDB

:: Start Kafka in KRaft mode
start cmd /k "cd C:\kafka && bin\windows\kafka-storage.bat format -t MkU3OEVBNTcwNTJENDM2Qk -c config\kraft\server.properties --ignore-formatted && bin\windows\kafka-server-start.bat config\kraft\server.properties"

echo All infra services are starting...
pause
