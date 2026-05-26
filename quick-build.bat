@echo off
echo Building all services...
for /d %%i in (*-service) do (
    if exist "%%i\pom.xml" (
        echo Building %%i...
        cd "%%i"
        mvn clean install
        cd ..
    )
)
echo Done!
pause