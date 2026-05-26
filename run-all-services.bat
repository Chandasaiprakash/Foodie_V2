@echo off
echo Starting All Microservices...

:: Start Config Service
start cmd /k "cd config-service\target && java -jar config-service-0.0.1-SNAPSHOT.jar"

:: Start Discovery Service
start cmd /k "cd discovery-service\target && java -jar discovery-service-0.0.1-SNAPSHOT.jar"

:: Start API Gateway
start cmd /k "cd gateway-service\target && java -jar gateway-service-0.0.1-SNAPSHOT.jar"

:: Start Auth Service
start cmd /k "cd auth-service\target && java -jar auth-service-0.0.1-SNAPSHOT.jar"

:: Start User Service
start cmd /k "cd user-service\target && java -jar user-service-0.0.1-SNAPSHOT.jar"

:: Start Restaurant Service
start cmd /k "cd restaurant-service\target && java -jar restaurant-service-0.0.1-SNAPSHOT.jar"

:: Start Order Service
start cmd /k "cd order-service\target && java -jar order-service-0.0.1-SNAPSHOT.jar"

:: Start Payment Service
start cmd /k "cd payment-service\target && java -jar payment-service-0.0.1-SNAPSHOT.jar"

:: Start Delivery Service
start cmd /k "cd delivery-service\target && java -jar delivery-service-0.0.1-SNAPSHOT.jar"

:: Start Notification Service
start cmd /k "cd notification-service\target && java -jar notification-service-0.0.1-SNAPSHOT.jar"

:: Start Cart Service
start cmd /k "cd cart-service\target && java -jar cart-service-0.0.1-SNAPSHOT.jar"

echo All services are starting in separate windows...
pause
