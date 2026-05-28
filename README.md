# 🍔 Foodie — Production-Grade Food Delivery Platform

> A full-stack, cloud-native food delivery application built with a **microservices architecture** on **Spring Boot 3 / Java 21**, a **React 19** frontend, and a complete **Kubernetes + Helm** deployment pipeline.

[![CI/CD](https://github.com/chandasaiprakash/foodie/actions/workflows/ci.yml/badge.svg)](https://github.com/chandasaiprakash/foodie/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Microservices-green?logo=spring)](https://spring.io/projects/spring-cloud)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-Event--Driven-black?logo=apachekafka)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-Cache-red?logo=redis)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8-blue?logo=mysql)](https://www.mysql.com/)
[![MongoDB](https://img.shields.io/badge/MongoDB-NoSQL-green?logo=mongodb)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Orchestrated-326CE5?logo=kubernetes)](https://kubernetes.io/)
[![AWS](https://img.shields.io/badge/AWS-Cloud-orange?logo=amazonaws)](https://aws.amazon.com/)
[![Terraform](https://img.shields.io/badge/Terraform-IaC-844FBA?logo=terraform)](https://www.terraform.io/)
[![Prometheus](https://img.shields.io/badge/Prometheus-Metrics-orange?logo=prometheus)](https://prometheus.io/)
[![Grafana](https://img.shields.io/badge/Grafana-Observability-F46800?logo=grafana)](https://grafana.com/)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-Tracing-purple?logo=opentelemetry)](https://opentelemetry.io/)
[![ELK Stack](https://img.shields.io/badge/ELK-Logging-yellow?logo=elastic)](https://www.elastic.co/elastic-stack/)
[![Jaeger](https://img.shields.io/badge/Jaeger-Distributed%20Tracing-66CFE3)](https://www.jaegertracing.io/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-Fault%20Tolerance-6DB33F)](https://resilience4j.readme.io/)
[![JUnit5](https://img.shields.io/badge/JUnit5-Testing-25A162?logo=junit5)](https://junit.org/junit5/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-Integration%20Testing-2496ED)](https://testcontainers.com/)

---

## 📑 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Microservices](#microservices)
- [Key Engineering Features](#key-engineering-features)
- [Frontend Features](#frontend-features)
- [Infrastructure & DevOps](#infrastructure--devops)
- [Observability](#observability)
- [CI/CD Pipeline](#cicd-pipeline)
- [Getting Started — Docker Compose (Local)](#getting-started--docker-compose-local)
- [Getting Started — Kubernetes (Helm)](#getting-started--kubernetes-helm)
- [Environment Variables & Secrets](#environment-variables--secrets)
- [API Routes (Gateway)](#api-routes-gateway)
- [Testing](#testing)
- [Project Structure](#project-structure)

---

## Architecture Overview

```
                        ┌──────────────────────────────────────┐
                        │           React 19 Frontend          │
                        │  (Vite · Tailwind · Framer Motion)   │
                        └────────────────┬─────────────────────┘
                                         │ HTTP / WebSocket
                        ┌────────────────▼─────────────────────┐
                        │         Spring Cloud Gateway          │
                        │  JWT Auth · Circuit Breaker · Retry  │
                        └──┬──────┬──────┬──────┬──────┬───────┘
                           │      │      │      │      │
              ┌────────────▼┐  ┌──▼───┐ ┌▼───┐ ┌▼──────▼──┐ ┌─────────────┐
              │ Auth Service│  │Order │ │Pay │ │Restaurant│ │  Cart /     │
              │  (JWT/BCrypt│  │Svc   │ │Svc │ │ Service  │ │ Delivery /  │
              │  Rate Limit)│  │MySQL │ │    │ │Mongo+ES  │ │ Notif Svc   │
              └─────────────┘  └──┬───┘ └─┬──┘ └──────────┘ └─────────────┘
                                  │        │
                        ┌─────────▼────────▼──────────────────┐
                        │               Apache Kafka           │
                        │  order-created · payment-completed   │
                        │  payment-failed · delivery-events    │
                        └─────────────────────────────────────┘
```

All services communicate asynchronously through Kafka using the **Transactional Outbox Pattern**, ensuring no message is ever lost even if a service crashes mid-transaction.

---

## Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Language | Java 21 (Virtual Threads / Project Loom) |
| Framework | Spring Boot 3.x |
| API Gateway | Spring Cloud Gateway |
| Messaging | Apache Kafka (KRaft mode, no ZooKeeper) |
| Databases | MySQL 8 · MongoDB 7 · Redis 7 |
| Search | Elasticsearch 8.15 |
| Payments | Razorpay (webhook-verified) |
| Resilience | Resilience4j (Circuit Breaker · Retry · Bulkhead · Rate Limiter) |
| Service-to-Service | OpenFeign |
| Auth | JWT (JJWT) · BCrypt |
| Build | Maven (multi-module) |

### Frontend
| Layer | Technology |
|---|---|
| Framework | React 19 · Vite 8 |
| Styling | Tailwind CSS · MUI · Framer Motion |
| Routing | React Router v7 |
| HTTP | Axios |
| Real-time | STOMP over SockJS (WebSocket) |
| State | React Context (Auth · Cart · Location) |
| UI Components | Headless UI · Heroicons · Lucide · Radix UI |

### Infrastructure
| Layer | Technology |
|---|---|
| Containers | Docker · Docker Compose |
| Orchestration | Kubernetes · Helm 3 · Kustomize |
| Cloud (IaC) | Terraform |
| Observability | Jaeger (OTLP) · Prometheus · Micrometer |
| CI/CD | GitHub Actions |

---

## Microservices

| Service | Port | Database | Responsibilities |
|---|---|---|---|
| **gateway-service** | 8080 | — | Reverse proxy, JWT validation, circuit breaking, CORS |
| **auth-service** | 8081 | MySQL | Register, login, `/me`, JWT issuance, rate limiting |
| **order-service** | 8082 | MySQL | Order creation, status management, outbox → Kafka |
| **payment-service** | 8083 | MySQL | Razorpay order creation & webhook verification, outbox → Kafka |
| **restaurant-service** | 8084 | MongoDB + Elasticsearch | Restaurant listing, menu items, fuzzy full-text search |
| **user-service** | 8085 | MySQL | User profiles, address management (internal API) |
| **delivery-service** | 8086 | MongoDB | Partner assignment, delivery status events |
| **notification-service** | 8087 | Redis | WebSocket broadcast to frontend, order/delivery push updates |
| **cart-service** | 8088 | MongoDB | Persistent cart per user |

---

## Key Engineering Features

### 1. Transactional Outbox Pattern
Every service that produces Kafka events uses the **Outbox Pattern** instead of dual-writes. An `OutboxEvent` row is persisted in the same database transaction as the business entity. A background `OutboxPoller` (running every 5 seconds) reads `PENDING` events and publishes them to Kafka, then marks them `PUBLISHED`. A nightly cleanup job removes published rows older than 7 days.

This guarantees **at-least-once delivery** with zero data loss even during crashes, network partitions, or Kafka downtime.

### 2. Idempotency (Dual-Layer Defence)
Kafka consumers in every downstream service (payment, delivery, notification) are fully idempotent:
- **Layer 1 — `IdempotencyService`:** An `IdempotencyService.claim(eventId)` call at the top of each listener rejects already-seen event IDs (backed by a `processed_events` table with a unique index).
- **Layer 2 — DB Unique Index:** The business entity itself (e.g., `Delivery.orderUuid`) has a unique MongoDB index; a `DuplicateKeyException` is silently absorbed on replay.
- The outbox skips writing a duplicate event on the idempotent path, preventing fan-out storms.

### 3. Resilience4j — Full Resilience Stack
Every inter-service or external call is wrapped with Resilience4j patterns:

| Pattern | Where Used | Config Highlight |
|---|---|---|
| Circuit Breaker | Gateway → all services, auth-service → user-service, payment → Razorpay | Opens at 40–50% failure rate; half-open probing |
| Retry with Exponential Backoff | Kafka publish (order/payment), Razorpay API calls | 3 attempts, 2× multiplier |
| Bulkhead | Razorpay calls, order creation | Max 10 concurrent Razorpay calls |
| Rate Limiter | `/auth/login`, `/auth/register`, `/payments/create`, `/payments/verify` | 20 req/sec auth, 50 req/sec payment creation |

Circuit breaker state is exposed via Spring Actuator health endpoints and tagged in Prometheus metrics.

### 4. Distributed Tracing — Correlation IDs
Every HTTP request entering the gateway is assigned (or propagates) an `X-Correlation-ID` header. A `CorrelationIdFilter` in each service writes this to MDC so it appears in every log line. A custom `KafkaCorrelationProducerInterceptor` stamps the correlation ID onto each Kafka message header, and a `KafkaCorrelationConsumerAspect` restores it in the consumer's MDC. This provides an unbroken trace chain from frontend request → gateway → services → Kafka → consumers.

### 5. Saga Pattern / Compensation
The distributed order flow is a choreography-based Saga:
1. **Order Service** creates order → publishes `order-created`
2. **Payment Service** listens → creates Razorpay order; on webhook success → publishes `payment-completed`; on failure → publishes `payment-failed`
3. **Delivery Service** listens to `payment-completed` → assigns partner → publishes `delivery-events`
4. **Order Service** listens to `payment-completed` / `payment-failed` → updates order payment status
5. **Notification Service** listens to `delivery-events` + `order-updated` → broadcasts over WebSocket

Integration tests (`SagaCompensationIT`) verify that a payment failure correctly unwinds the order state.

### 6. Dead Letter Queue (DLQ)
Every Kafka consumer is configured with a Dead Letter Queue. Messages that fail processing after retries are routed to `<topic>.DLQ`. Each service exposes an `/internal/dead-letters` operator API to inspect, retry, or discard DLQ entries. The gateway blocks all `/internal/**` paths with HTTP 403 — operator access requires direct pod/internal LB access.

### 7. Java 21 Virtual Threads
All Tomcat-based services (auth, order, payment, restaurant, user, delivery, notification, cart) run with `spring.threads.virtual.enabled=true`, replacing the traditional blocking thread pool with JVM Project Loom virtual threads. The gateway (WebFlux/Netty) intentionally does not use virtual threads as it's already non-blocking.

### 8. Elasticsearch Full-Text & Location-Aware Search
The restaurant service syncs MongoDB data into Elasticsearch on startup via `DataSyncRunner`. Search queries use a custom `@Query` that executes a `bool/should` query combining:
- **Top-level fuzzy match** on `restaurantName` and `cuisineType`
- **Nested fuzzy match** on menu item `name` and `description`
- **Location filter** (city term filter in `must` clause) when a user location is set

This means searching "biryani" returns restaurants that serve biryani even if "biryani" doesn't appear in the restaurant name.

### 9. Razorpay Integration with Webhook Verification
The payment service integrates Razorpay in two phases:
1. **Order Creation** — creates a Razorpay order via `ResilientRazorpayService` (wrapped with circuit breaker + retry + bulkhead).
2. **Webhook Verification** — a dedicated `RazorpayWebhookController` receives `payment.captured` and `payment.failed` events, verifies the HMAC-SHA256 signature using the webhook secret, then dispatches the appropriate Kafka event through the outbox.

### 10. Security — Email Enumeration Prevention
The `/auth/login` endpoint returns the same generic `"Invalid credentials"` 401 response whether the email doesn't exist or the password is wrong, preventing attackers from enumerating valid user emails.

---

## Frontend Features

### Pages & Routing
| Page | Route | Auth Required |
|---|---|---|
| Home | `/` | No |
| Search Results | `/search-results` | No |
| Restaurants | `/restaurants` | No |
| Menu | `/restaurants/:restaurantId` | No |
| Login | `/login` | No (redirects if logged in) |
| Register | `/register` | No (redirects if logged in) |
| Cart | `/cart` | Yes |
| Payment | `/payment` | Yes |
| Orders | `/orders` | Yes |
| Profile | `/profile` | Yes |

### Real-Time Order Tracking
The Orders page opens a **STOMP over SockJS WebSocket** connection to the notification service. When a delivery or order status event arrives on `/topic/updates`, the UI:
- Highlights the updated order with an animation
- Pops a toast notification
- Plays an audio notification sound

### Payment UI
A full payment flow with support for:
- **UPI** (UPI ID input + QR-style)
- **Card Payment** (card number, expiry, CVV inputs)
- Razorpay order creation on the backend, with success/failure redirect screens

### Location Detection
On first load the app requests browser geolocation permission. Granted coordinates are reverse-geocoded via **OpenCage API** to extract the city name, stored in `localStorage`, and used to filter restaurant search results by city.

### Cart & Reorder
- Persistent cart state via `CartContext` and the backend `cart-service`
- Reorder functionality on the Orders page: clears current cart and re-adds all items from a previous order

### Animated Page Transitions
All route changes use **Framer Motion** `AnimatePresence` with slide + fade transitions for a polished UX.

### Dark Mode Support
Logo assets include both `LightLogo.jpg` and `DarkLogo.jpg` variants, and the navbar adapts styling for theme changes.

---

## Infrastructure & DevOps

### Docker Compose (Local Development)
A single `docker-compose.yml` at the root spins up the entire platform:

```
kafka (KRaft)  ·  mysql  ·  mongo  ·  mongo-seed
redis  ·  elasticsearch  ·  jaeger  ·  kafka-ui
user-service  ·  auth-service  ·  order-service
payment-service  ·  restaurant-service  ·  delivery-service
notification-service  ·  cart-service  ·  gateway-service  ·  frontend
```

MongoDB is pre-seeded with restaurant data from `foodie_restaurant.restaurants` via a `mongo-seed` init container that is idempotent (skips seeding if data already exists).

### Kubernetes — Helm Chart
A Helm chart at `infra/helm/foddie/` deploys all services with:
- Per-service `Deployment`, `Service`, and `HorizontalPodAutoscaler`
- HPA targets: CPU 70%, Memory 80%
- Gateway replicas: 2–8; other services: 2 replicas by default
- ConfigMap and Secret references per service (`foodie-db-credentials`, `foodie-jwt-secret`, `foodie-internal-secret`, `foodie-razorpay-secrets`)
- `/internal/**` blocked at the gateway level (returns 403)

### Kubernetes — Kustomize Base
A Kustomize base at `infra/k8s/` provides raw manifests for namespace, configmaps, secrets, platform services, and an observability stack.

### Terraform (IaC)
Terraform modules under `infra/terraform/` provision the cloud infrastructure with modular, reusable components.

---

## Observability

### Distributed Tracing — Jaeger
All services export OTLP traces to Jaeger at `http://jaeger:4318/v1/traces`. 100% sampling in development (`management.tracing.sampling.probability=1.0`). Jaeger UI is accessible at `http://localhost:16686`.

### Metrics — Prometheus
Every service exposes `/actuator/prometheus` with an `application` tag matching the service name. Circuit breaker health is exposed at `/actuator/health/circuitbreakers`.

### Kafka UI
Kafka UI is available at `http://localhost:8090` for inspecting topics, consumer group lag, and message contents.

### Structured Logging
All services use Logback with a structured format. The `X-Correlation-ID` is present on every log line via MDC, making it trivial to trace a single user request across all services in any log aggregator (ELK, Loki, CloudWatch).

---

## CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push to `main`, `master`, `develop`, and on all pull requests. It has four parallel/staged jobs:

| Job | Steps |
|---|---|
| **backend** | Java 21 setup → `mvn clean verify -DskipITs` (unit tests only, fast) → Upload JARs |
| **frontend** | Node 22 setup → npm cache → `npm ci && npm run build` |
| **docker-build** | Depends on backend + frontend → Build Docker image for `order-service` with GitHub Actions cache |
| **infra-validation** | Helm lint + template render → Kustomize render (validates all K8s manifests) |

A separate `integration-tests.yml` workflow runs the full integration test suite (Testcontainers-based).

---

## Getting Started — Docker Compose (Local)

### Prerequisites
- Docker Desktop (or Docker Engine + Compose plugin)
- Razorpay test account (for payment features)

### 1. Clone the repository
```bash
git clone https://github.com/<your-org>/Foodie_V2.git
cd Foodie_V2
```

### 2. Set Razorpay credentials
```bash
export RAZORPAY_KEY_ID=rzp_test_your_key_id
export RAZORPAY_KEY_SECRET=your_key_secret
export RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
```
Or create a `.env` file at the project root with those three variables.

### 3. Start infrastructure + services
```bash
docker-compose up --build
```
> First build takes a few minutes. Subsequent builds use layer cache.

### 4. Quick-start helper (Windows)
```bat
run-infra.bat          # Start only infra containers (Kafka, MySQL, Mongo, Redis, ES, Jaeger)
run-all-services.bat   # Build and start all Java services
stop-all-services.bat  # Stop everything
quick-build.bat        # Fast rebuild
```

### 5. Access the application
| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Jaeger UI | http://localhost:16686 |
| Kafka UI | http://localhost:8090 |
| Elasticsearch | http://localhost:9200 |

---

## Getting Started — Kubernetes (Helm)

### Prerequisites
- `kubectl` configured against your cluster
- `helm` 3.x installed
- Container registry with images pushed (set `global.registry` and `global.imageTag`)

### Deploy
```bash
# Create namespace
kubectl apply -f infra/k8s/namespace.yaml

# Apply secrets (edit with real values first)
kubectl apply -f infra/k8s/secrets.yaml -n foodie

# Deploy with Helm
helm upgrade --install foodie infra/helm/foddie \
  --namespace foodie \
  -f infra/helm/foddie/Values.yaml \
  --set global.imageTag=$(git rev-parse --short HEAD)
```

### Validate manifests (dry-run)
```bash
helm lint infra/helm/foddie -f infra/helm/foddie/Values.yaml
helm template foodie infra/helm/foddie -f infra/helm/foddie/Values.yaml
kubectl kustomize infra/k8s
```

---

## Environment Variables & Secrets

| Variable | Service | Description |
|---|---|---|
| `RAZORPAY_KEY_ID` | payment-service | Razorpay API key ID |
| `RAZORPAY_KEY_SECRET` | payment-service | Razorpay API secret |
| `RAZORPAY_WEBHOOK_SECRET` | payment-service | Webhook HMAC secret |
| `JWT_SECRET` | gateway-service, auth-service | Shared JWT signing key |
| `USER_SERVICE_URL` | auth-service | Internal URL to user-service |
| `SPRING_DATASOURCE_URL` | all MySQL services | JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | all MySQL services | DB username |
| `SPRING_DATASOURCE_PASSWORD` | all MySQL services | DB password |
| `SPRING_DATA_MONGODB_URI` | restaurant, delivery, cart, notification | MongoDB connection URI |
| `SPRING_ELASTICSEARCH_URIS` | restaurant-service | Elasticsearch base URL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | all Kafka services | Kafka broker address |
| `REDIS_HOST` / `REDIS_PORT` | notification-service | Redis connection |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | all services | Jaeger OTLP collector URL |

> ⚠️ Never commit real credentials. Use environment variables, `.env` files (git-ignored), or Kubernetes Secrets.

---

## API Routes (Gateway)

All routes are prefixed with `/api` and served through the gateway on port `8080`.

| Method | Path | Auth | Service | Description |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | No | auth-service | Register new user |
| `POST` | `/api/auth/login` | No | auth-service | Login, returns JWT |
| `GET` | `/api/auth/me` | Bearer | auth-service | Get current user from token |
| `GET` | `/api/restaurants` | No | restaurant-service | List all restaurants |
| `GET` | `/api/restaurants?city=Hyderabad` | No | restaurant-service | Filter by city |
| `GET` | `/api/restaurants/search?query=biryani` | No | restaurant-service | Elasticsearch fuzzy search |
| `GET` | `/api/menu-items/{restaurantId}` | No | restaurant-service | Get menu for restaurant |
| `POST` | `/api/orders` | Bearer | order-service | Create new order |
| `GET` | `/api/orders` | Bearer | order-service | Get user's orders (paginated) |
| `POST` | `/api/payments/create` | Bearer | payment-service | Create Razorpay payment order |
| `POST` | `/api/payments/verify` | Bearer | payment-service | Verify payment after frontend callback |
| `POST` | `/api/payments/webhook` | Razorpay Signature | payment-service | Razorpay webhook receiver |
| `GET` | `/api/deliveries/{orderUuid}` | Bearer | delivery-service | Get delivery status |
| `GET` | `/api/cart` | Bearer | cart-service | Get user's cart |
| `POST` | `/api/cart` | Bearer | cart-service | Save/update cart |
| `DELETE` | `/api/cart` | Bearer | cart-service | Clear cart |
| `WS` | `/ws/**` | — | notification-service | WebSocket endpoint (STOMP) |

---

## Testing

### Unit Tests
Each service contains JUnit 5 unit tests under `src/test/`. Run all:
```bash
mvn test
```

### Integration Tests (Testcontainers)
The order-service and payment-service have a comprehensive suite of integration tests that spin up real Kafka, MySQL, and MongoDB containers via Testcontainers:

| Test | What it verifies |
|---|---|
| `KafkaPublishConsumeIT` | End-to-end Kafka publish and consume |
| `IdempotencyIT` | Duplicate Kafka messages are safely ignored |
| `DuplicateEventIT` | Concurrent duplicate events don't create multiple records |
| `ReplaySafetyIT` | Kafka replay after rebalance doesn't corrupt state |
| `DlqRoutingIT` | Poison-pill messages route to DLQ after retries |
| `RetryHandlingIT` | Transient failures trigger retry with backoff |
| `SagaCompensationIT` | Payment failure correctly compensates the order saga |
| `DbCommitAckFailureIT` | DB commit failure before Kafka ACK is handled safely |

Run integration tests:
```bash
mvn verify  # includes ITs
```

Skip integration tests (fast build):
```bash
mvn verify -DskipITs
```

### Frontend Tests
```bash
cd Foodie-App-Frontend
npm test
```

---

## Project Structure

```
Foodie_V2/
├── docker-compose.yml              # Full local stack
├── Dockerfile.service              # Shared multi-stage Dockerfile (all Java services)
├── pom.xml                         # Maven multi-module parent
│
├── gateway-service/                # Spring Cloud Gateway (port 8080)
├── auth-service/                   # JWT auth + BCrypt (port 8081)
├── order-service/                  # Order management + Outbox (port 8082)
├── payment-service/                # Razorpay + webhook + Outbox (port 8083)
├── restaurant-service/             # MongoDB + Elasticsearch search (port 8084)
├── user-service/                   # User profiles (port 8085, internal)
├── delivery-service/               # Delivery assignment + Outbox (port 8086)
├── notification-service/           # WebSocket broadcast via STOMP (port 8087)
├── cart-service/                   # Persistent cart (port 8088)
│
├── Foodie-App-Frontend/            # React 19 + Vite frontend
│   ├── src/
│   │   ├── components/             # Reusable UI (Navbar, SearchBar, PaymentScreens…)
│   │   ├── context/                # AuthContext, CartContext, LocationContext
│   │   ├── pages/                  # Home, Login, Register, Menu, Cart, Orders, Payment…
│   │   └── utils/                  # Axios instance, API config
│   └── Dockerfile                  # Nginx-based production image
│
├── infra/
│   ├── helm/foddie/                # Helm chart (all services, HPA, secrets)
│   ├── k8s/                        # Kustomize base manifests
│   └── terraform/                  # Cloud infrastructure IaC
│
└── .github/workflows/
    ├── ci.yml                      # Main CI (build + test + docker + helm validate)
    └── integration-tests.yml       # Full integration test suite
```

---

## Design Decisions & Patterns Summary

| Pattern | Where Applied | Why |
|---|---|---|
| **Transactional Outbox** | order, payment, delivery | Eliminate dual-write; guarantee at-least-once Kafka delivery |
| **Idempotent Consumers** | payment, delivery, notification | Safe Kafka replay, rebalance, and retry |
| **Saga (Choreography)** | order → payment → delivery → notification | Distributed transaction without 2PC |
| **Circuit Breaker** | gateway → services, auth → user, payment → Razorpay | Prevent cascade failures |
| **Dead Letter Queue** | all Kafka consumers | Capture unprocessable messages without blocking the partition |
| **Virtual Threads** | all Tomcat services | Eliminate thread pool exhaustion under high concurrency |
| **Correlation ID propagation** | HTTP + Kafka headers + MDC | End-to-end traceability across services |
| **Email enumeration prevention** | auth-service login | Security — same error for missing user and wrong password |
| **Internal API blocking** | gateway `/internal/**` → 403 | Operator endpoints never exposed to internet |

---

*Built with ❤️ — a complete, production-ready food delivery platform demonstrating event-driven microservices, distributed systems reliability patterns, and modern cloud-native deployment.*
