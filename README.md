# 🍔 FOODIE — Cloud-Native Distributed Food Ordering Platform

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=flat&logo=springboot&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat&logo=apachekafka&logoColor=white)
![AWS](https://img.shields.io/badge/AWS_EKS-FF9900?style=flat&logo=amazonaws&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-623CE4?style=flat&logo=terraform&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)

> A production-grade microservices platform demonstrating **Event-Driven Architecture**, **Outbox Pattern**, **Saga Pattern**, **Idempotent Consumers**, **Dead-Letter Replay Safety**, **Infrastructure as Code**, and **Zero-Downtime Deployment** on AWS EKS.

---

## Table of contents

1. [System architecture](#system-architecture)
2. [Services](#services)
3. [Kafka event flow](#kafka-event-flow)
4. [Outbox pattern](#outbox-pattern)
5. [Idempotency](#idempotency)
6. [Dead-letter replay safety](#dead-letter-replay-safety)
7. [Resilience patterns](#resilience-patterns)
8. [Tech stack](#tech-stack)
9. [Getting started locally](#getting-started-locally)
10. [Infrastructure & DevOps](#infrastructure--devops)
11. [Observability](#observability)
12. [Security](#security)
13. [API reference](#api-reference)
14. [Project structure](#project-structure)
15. [Key design decisions](#key-design-decisions)

---

## System architecture

FOODIE uses a **Choreography-based Saga** pattern. No central orchestrator — services react to events published on Kafka topics through the **Outbox Pattern**, which guarantees exactly-once writes to the database even when Kafka is temporarily unavailable. Every consumer is **idempotent** and every dead-lettered message is **persisted and replayable** via an operator API.

```
Client
  │
  ▼
API Gateway (port 8080)    ← JWT validation, rate limiting, circuit breaker, routing
  │
  ├── auth-service         ← register / login / JWT issuance
  ├── user-service         ← user profile management
  ├── order-service        ← place & track orders  [Producer via Outbox]
  ├── payment-service      ← Razorpay integration   [Producer via Outbox]
  ├── delivery-service     ← assign partners        [Producer via Outbox]
  ├── restaurant-service   ← catalog (MongoDB + Elasticsearch)
  ├── notification-service ← WebSocket real-time push
  └── cart-service         ← Redis-backed session cart
```

**Service discovery:** Eureka (`discovery-service` on port 8761).  
**Config server:** Spring Cloud Config (`config-service` on port 8888).  
**Persistence strategy:**
- MySQL — order-service, payment-service, auth-service, user-service (ACID, relational)
- MongoDB — restaurant-service, delivery-service (flexible schema, horizontal scaling)
- Redis — cart-service, gateway JWT cache, notification idempotency, notification dead-letter store

---

## Services

| Service | Port | Database | Kafka role |
|---|---|---|---|
| `gateway-service` | 8080 | Redis (JWT cache) | — |
| `auth-service` | 8081 | MySQL (`foodie_auth`) | — |
| `user-service` | 8085 | MySQL (`foodie_users`) | — |
| `order-service` | 8082 | MySQL (`foodie_orders`) | **Producer** (Outbox) + Consumer |
| `payment-service` | 8083 | MySQL (`foodie_payments`) | **Producer** (Outbox) + Consumer |
| `delivery-service` | 8086 | MongoDB (`foodie_delivery`) | **Producer** (Outbox) + Consumer |
| `restaurant-service` | 8084 | MongoDB + Elasticsearch | — |
| `notification-service` | 8087 | Redis (idempotency + dead-letters) | Consumer |
| `cart-service` | 8088 | Redis | — |

---

## Kafka event flow

Every Kafka listener is **idempotent** (deduplication via `processed_events` table/collection) and has **automatic retry + DLT** via `@RetryableTopic` (4 attempts, exponential back-off 1 s → 2 s → 4 s → 8 s). Every Kafka producer uses the **Outbox Pattern** — events are persisted to the database atomically with the business state change before Kafka publish. Every dead-lettered message is **persisted and replayable** — see [Dead-letter replay safety](#dead-letter-replay-safety).

```
1. POST /api/orders
        │
        ▼
   order-service  ──[outbox]──► order-created ──────────► payment-service
                                                                │
                                         ┌──────────────────────┤
                                         │                      │
                                 payment-completed        payment-failed
                                         │                      │
                            ┌────────────┘              order-service
                            │                          (status → CANCELLED)
              ┌─────────────┤
              │             │
        order-service  delivery-service
     (status → CONFIRMED)   │
      order-updated──► [outbox]──► delivery-events ──► order-service
              │                                              │
              └──► notification-service        order-updated──► notification-service
```

### Topics

| Topic | Producer | Consumers |
|---|---|---|
| `order-created` | order-service (Outbox) | payment-service |
| `payment-completed` | payment-service (Outbox) | order-service, delivery-service |
| `payment-failed` | payment-service (Outbox) | order-service |
| `order-updated` | order-service | notification-service |
| `delivery-events` | delivery-service (Outbox) | order-service, notification-service |

Dead-letter topics are auto-created with the suffix `-dlt` (e.g. `order-created-dlt`, `payment-completed-dlt`). All dead-lettered messages are persisted to a `dead_letters` store and exposed for operator replay — see [Dead-letter replay safety](#dead-letter-replay-safety).

---

## Outbox pattern

### The problem it solves

A direct Kafka publish after a DB commit has a **dual-write window**: if Kafka is unavailable or the service crashes between `orderRepository.save()` and `kafkaTemplate.send()`, the event is silently lost and the Saga is stuck. The order exists in the database but payment-service never receives `order-created` — the customer paid but the order stays `PENDING` forever.

### How it works in FOODIE

All three producer services (order-service, payment-service, delivery-service) implement the Outbox Pattern:

```
HTTP Request
     │
     ▼
@Transactional method
     ├── 1. Save business entity  (e.g. Order row)
     └── 2. Save OutboxEvent row  (same transaction)
          └── Commit ──► both rows durable, or neither
                              │
                              ▼ (async, every 5 seconds)
                         OutboxPoller
                              ├── Read PENDING rows
                              ├── Publish to Kafka
                              └── Mark PUBLISHED
```

### Failure modes covered

| Scenario | Before Outbox | With Outbox |
|---|---|---|
| Kafka broker down at commit time | HTTP 500, order not created | Order saved, event queued — poller retries when Kafka recovers |
| Service crash after DB commit | Event lost silently, Saga stuck | Poller picks up PENDING rows on restart |
| Kafka slow (high latency) | HTTP thread blocked until timeout | Immediate response, publish is async |
| Duplicate publish (at-least-once) | Double processing | Consumers are idempotent via `processed_events` |

### Implementation per service

**order-service** — MySQL/JPA

```
outbox/
├── OutboxEvent.java              # JPA @Entity, outbox_events table
├── OutboxEventRepository.java    # Spring Data JPA repository
├── OutboxEventService.java       # Saves rows inside caller's @Transactional
├── OutboxPoller.java             # @Scheduled every 5s, publishes PENDING rows
└── OutboxCleanupScheduler.java   # Daily at 02:00, deletes PUBLISHED rows older than 7d
```

`OrderService.createOrder()` saves the Order and writes an `outbox_events` row atomically:

```java
@Transactional
public Order createOrder(Order orderRequest, String customerEmail) {
    Order saved = orderRepository.save(order);
    outboxEventService.save("order-created", saved.getOrderUuid(), event); // same TX
    return saved;
    // Both rows commit here, or neither does.
}
```

**payment-service** — MySQL/JPA  
Same structure as order-service. `markSuccess()` and `markFailed()` each write an outbox row in the same transaction as the payment status update.

**delivery-service** — MongoDB  
Uses a MongoDB `outbox_events` collection. The `@Indexed(expireAfterSeconds = 604800)` TTL index on `expiresAt` auto-purges PUBLISHED documents after 7 days at the MongoDB level — no cron job needed.

### OutboxPoller behaviour

- Polls every 5 seconds (configurable via `outbox.poller.fixed-delay-ms`)
- On Kafka failure: increments `retry_count`, retries on next cycle
- After 5 retries: marks status `FAILED` and logs `ERROR` — alert on FAILED rows in production

---

## Idempotency

Every Kafka consumer implements multi-layer idempotency to handle at-least-once delivery safely.

### Consumer idempotency (all services)

Before processing any event, the listener atomically claims a deduplication key:

```java
String eventId = "payment-completed::" + event.getOrderUuid();
if (!idempotencyService.claim(eventId)) {
    return; // duplicate — skip
}
// safe to process
```

`IdempotencyService.claim()` uses `REQUIRES_NEW` propagation and `saveAndFlush()`. The first call inserts a `processed_events` row; the second throws `DataIntegrityViolationException` (unique index) and returns `false`. Both the claim insert and the business-logic update share the same `@Transactional` boundary — if the business update rolls back, the claim also rolls back, allowing a clean retry.

### Producer idempotency (payment-service)

`PaymentService.markSuccess()` and `markFailed()` guard against terminal-state regression:

```java
if ("SUCCESS".equals(payment.getStatus())) {
    return payment; // Razorpay webhook retry — no-op, no duplicate outbox row
}
```

### Delivery defence-in-depth

delivery-service applies three idempotency layers:
1. `IdempotencyService.claim()` in `PaymentEventListener` — blocks most duplicates
2. MongoDB unique index on `Delivery.orderUuid` — catches concurrent race conditions
3. `DuplicateKeyException` handler in `DeliveryService.assignForOrder()` — no event re-queued on the idempotent path

### processed_events cleanup

| Service | Storage | Retention |
|---|---|---|
| order-service | MySQL `processed_events` | 7 days (IdempotencyCleanupScheduler) |
| payment-service | MySQL `processed_events` | 7 days (IdempotencyCleanupScheduler) |
| delivery-service | MongoDB `processed_events` | 7 days (MongoDB TTL index) |
| notification-service | Redis SETNX | 24 hours (Redis TTL) |

---

## Dead-letter replay safety

### The problem with bare `@DltHandler`

`@RetryableTopic` automatically routes exhausted messages to a dead-letter topic (suffix `-dlt`). Without additional handling, the default `@DltHandler` just logs a line and the message is gone — no visibility, no recovery path. An orphaned order, a missed payment cancellation, or a dropped notification is silently swallowed.

### What FOODIE does instead

Every `@DltHandler` in every service now **persists the failed message** to a service-local dead-letter store and exposes an **operator API** for inspection and replay.

#### Storage strategy per service

Each service uses its existing persistence technology — no new infrastructure is required:

| Service | Dead-letter store | Why |
|---|---|---|
| order-service | MySQL `dead_letters` (JPA) | Already has MySQL; strong consistency, SQL queries |
| payment-service | MySQL `dead_letters` (JPA) | Already has MySQL |
| delivery-service | MongoDB `dead_letters` collection | Already uses MongoDB |
| notification-service | Redis hashes (`notif:dl:{id}`) | No dedicated DB; Redis is already present |

#### Replay state machine

```
PENDING ──► REPLAYING ──► REPLAYED
                │
                └──► REPLAYING  (replay publish failed — operator must retry or ignore)

PENDING ──► IGNORED   (operator marks as known / acceptable failure)
```

State is written to the store **before** the Kafka publish. If the service crashes during replay, the row is left in `REPLAYING` (visible to operators) rather than silently lost.

#### Replay safety guarantees

1. **Idempotent store** — `(source_topic, original_key)` unique constraint. If the DLT re-delivers a message, no second row is created. The existing row retains its current replay status so an in-progress operator decision is not lost.
2. **Atomic status transition** — `PENDING → REPLAYING` commits before the Kafka publish. A crash during publish leaves a visible `REPLAYING` record, not silence.
3. **Guard-rails** — replaying a `REPLAYED` or `IGNORED` row returns `false` immediately. No double-replay is possible via the API.
4. **Payload fidelity** — the raw JSON captured at DLT arrival is re-published byte-for-byte. The downstream consumer's existing idempotency guard suppresses double-processing if the original message was already handled before it landed on the DLT.
5. **Topic derivation** — original topic is derived by stripping the `-dlt` suffix. `payment-completed-dlt → payment-completed`. No hardcoded topic names.

#### Dead-letter schema

Every `dead_letters` record stores:

| Field | Description |
|---|---|
| `source_topic` | DLT topic name (e.g. `payment-completed-dlt`) |
| `original_key` | Kafka message key |
| `payload_json` | Raw JSON payload — preserved verbatim for replay |
| `aggregate_id` | Business ID extracted from the payload (e.g. `orderUuid`) |
| `last_exception_class` | Exception class that caused exhaustion |
| `last_exception_message` | Exception message |
| `failed_at` | Timestamp when the message landed on the DLT |
| `retry_count` | Number of delivery attempts before landing here |
| `correlation_id` | Distributed trace ID for cross-service linkage |
| `replay_status` | `PENDING` \| `REPLAYING` \| `REPLAYED` \| `IGNORED` |
| `replayed_at` | Timestamp of successful replay |
| `replay_note` | Human-readable status note / failure message |

#### Operator API

Each service exposes an internal REST API under `/internal/dead-letters`. The gateway blocks all `/internal/**` routes from external traffic (returns 403) — ops tooling must reach services directly (pod exec, internal load balancer, kubectl port-forward).

```
GET  /internal/dead-letters          — list all dead letters
GET  /internal/dead-letters/pending  — list PENDING only
POST /internal/dead-letters/{id}/replay        — replay a message
POST /internal/dead-letters/{id}/ignore?reason= — mark as IGNORED
```

**Replay a dead letter** (order-service example):

```bash
# List PENDING dead letters
curl http://order-service:8082/internal/dead-letters/pending

# Replay by id
curl -X POST http://order-service:8082/internal/dead-letters/42/replay

# Mark as ignored (known data issue, safe to skip)
curl -X POST "http://order-service:8082/internal/dead-letters/42/ignore?reason=Ghost+order+from+load+test"
```

**Response (replay)**:
```json
{ "success": true, "message": "Replay submitted" }
```

**Response (terminal state — not replayable)**:
```json
{ "success": false, "message": "Dead letter is not in a replayable state" }
```

#### DLT handler example (order-service `PaymentEventListener`)

```java
@DltHandler
public void handleDlt(
        PaymentCompletedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
        @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

    log.error("DLT: payment-completed exhausted retries — storing for replay. topic={} orderUuid={}",
              topic, event.getOrderUuid());

    deadLetterService.store(
        topic, key,
        DltHandlerSupport.toJson(event),   // raw JSON preserved verbatim
        event.getOrderUuid(),              // aggregate id for quick filtering
        exceptionClass, exceptionMessage,
        4,                                 // retry count
        event.getCorrelationId()           // trace linkage
    );
}
```

#### Dead-letter coverage

| Service | DLT topic | Listener |
|---|---|---|
| order-service | `payment-completed-dlt` | `PaymentEventListener` |
| order-service | `payment-failed-dlt` | `PaymentFailedEventListener` |
| order-service | `delivery-events-dlt` | `DeliveryEventListener` |
| payment-service | `order-created-dlt` | `PaymentListener` |
| delivery-service | `payment-completed-dlt` | `PaymentEventListener` |
| notification-service | `order-updated-dlt` | `OrderEventListener` |
| notification-service | `delivery-events-dlt` | `OrderEventListener` |

#### Integration tests (`ReplaySafetyIT`)

`order-service` includes a full integration test suite covering:

- Orphan message exhausts retries → persists `DeadLetter` row with `PENDING` status
- DLT re-delivery is idempotent — no second row created
- `IGNORED` state transition works and persists the reason
- Replay on a `IGNORED` or `REPLAYED` row returns `false` without changing state

---

## Resilience patterns

### order-service

| Pattern | Config | Purpose |
|---|---|---|
| CircuitBreaker | `kafkaPublish` — 50% failure rate, 20s open window | Protects outbox poller Kafka sends |
| Retry | 3 attempts, 500ms backoff (2x multiplier) | Transient broker hiccups |
| Bulkhead | 50 concurrent order creation calls, 500ms wait | Prevents thread starvation under load |

### payment-service

| Pattern | Config | Purpose |
|---|---|---|
| CircuitBreaker | `razorpay` — 40% failure rate, 30s open window | Protects against Razorpay API outage |
| Retry | 3 attempts, 1s backoff | Razorpay transient 5xx |
| Bulkhead | 10 concurrent Razorpay calls | Razorpay API rate limit awareness |
| RateLimiter | 50 req/s on `/payments/create`, 100 req/s on `/payments/verify` | Protects against burst traffic |

### delivery-service

| Pattern | Config | Purpose |
|---|---|---|
| CircuitBreaker | `orderServiceClient` — 50% failure rate, 15s open window | Protects Feign calls to order-service |
| Retry | 2 attempts, 500ms wait | Connection errors only |
| Bulkhead | 20 concurrent order-service calls | Prevents cascading failure |

### gateway-service

| Pattern | Per route | Purpose |
|---|---|---|
| CircuitBreaker | order, payment, delivery services | Returns fallback response, not 504 |
| Retry | 3 retries on SERVER_ERROR | Transient upstream errors |
| JWT Cache | Redis-backed | Avoids re-validating every request against auth-service |

---

## Tech stack

| Category | Technology |
|---|---|
| Language & runtime | Java 24, Spring Boot 3.5.5 — virtual threads enabled on all Tomcat services |
| Service mesh | Spring Cloud 2025 — Eureka, Gateway, OpenFeign, Config Server |
| Messaging      | Apache Kafka — Outbox Pattern, idempotent consumers, `@RetryableTopic`, DLT, dead-letter replay |
| Databases | MySQL 8, MongoDB 7, Redis 7 |
| Auth | JWT (HS256), BCrypt, gateway-level filter, `X-User-Email` / `X-User-Id` header injection |
| Payment | Razorpay — order creation, webhook with HMAC-SHA256 verification |
| Search | Elasticsearch — fuzzy multi-field, city-filter, `DataSyncRunner` for warm-up |
| Real-time | WebSocket (STOMP) via notification-service |
| Infrastructure | AWS EKS, Terraform (VPC + EKS + RDS modules), Helm |
| Containers | Docker multi-stage builds — JRE-only runtime image, non-root user, ZGC |
| CI/CD | GitHub Actions — matrix build per service → ECR push → Helm upgrade |
| Observability | OpenTelemetry, Jaeger, Prometheus, Grafana, Logback JSON (ELK-ready) |
| Resilience | Resilience4j — CircuitBreaker, Retry, Bulkhead, RateLimiter |

---

## Getting started locally

The local stack now boots from the root compose file with one command:

```bash
docker compose up --build -d
```

That command starts the React frontend, API gateway, all Spring services, MongoDB seed job, Kafka, MySQL, Redis, Elasticsearch, Jaeger, and Kafka UI. Open the app at [http://localhost:3000](http://localhost:3000).

### Prerequisites

- Docker Desktop (with Compose v2)
- Java 21+, Maven 3.9+
- Razorpay test account ([dashboard.razorpay.com](https://dashboard.razorpay.com))

### 1. Set Razorpay credentials

Credentials are **never stored in the repo**. Export them in your shell:

```bash
export RAZORPAY_KEY_ID=rzp_test_<your_key>
export RAZORPAY_KEY_SECRET=<your_secret>
export RAZORPAY_WEBHOOK_SECRET=<your_webhook_secret>
```

### 2. Clone the repo

```bash
git clone https://github.com/chandasaiprakash/foodie.git
cd foodie
```

### 3. Start the full stack

Starts the frontend, gateway, backend services, data stores, seed job, Kafka UI, and Jaeger:

```bash
docker compose up --build -d
```

Kafka UI: [http://localhost:8090](http://localhost:8090) — use it to inspect topics, consumer group lag, and DLT message counts.

### 4. Build all services manually

Compose builds all service images automatically. For a local JVM build without Docker:

```bash
mvn -Dmaven.test.skip=true package
```

### 5. Start or restart the stack

```bash
docker compose up --build -d
```

Windows convenience scripts at the repo root:
- `run-infra.bat` — start infrastructure
- `quick-build.bat` — build all JARs
- `run-all-services.bat` — start all microservices
- `stop-all-services.bat` — stop everything
- `kafka-reset.bat` — wipe and recreate all Kafka topics (local dev)

### 6. Verify

Current compose URLs:

| URL | Expected |
|---|---|
| http://localhost:3000 | Foodie frontend |
| http://localhost:8080/actuator/health | Gateway health |
| http://localhost:8080/api/restaurants?location=Hyderabad | Restaurant data through the gateway |
| http://localhost:8090 | Kafka UI |
| http://localhost:16686 | Jaeger UI |

### Service startup order

Infrastructure services (Kafka, MySQL, MongoDB, Redis) must be running before application services. The Compose file handles `depends_on` for infrastructure. In Kubernetes, services discover each other via ClusterIP DNS — no startup ordering dependency on a discovery service.

### Running tests

Each service has:
- **Unit tests** (`OrderServiceTest`, `PaymentServiceTest`, etc.)
- **Integration tests** — Testcontainers (real Kafka + MySQL/MongoDB containers)

```bash
# Unit tests only
mvn test

# All tests including integration
mvn verify
```

Integration test suites in order-service:

| Test class | What it verifies |
|---|---|
| `KafkaPublishConsumeIT` | Full publish/consume round trip |
| `IdempotencyIT` | Duplicate event suppression |
| `DuplicateEventIT` | Concurrent duplicate handling |
| `DlqRoutingIT` | Exhausted events route to DLT topic |
| `RetryHandlingIT` | Exponential backoff behaviour |
| `SagaCompensationIT` | Payment failure rolls back order status |
| `DbCommitAckFailureIT` | Outbox durability under commit/ack failure |
| `ReplaySafetyIT` | Dead-letter persistence, idempotent DLT re-delivery, IGNORED state, replay guard on terminal rows |

---

## Infrastructure & DevOps

### Terraform (`infra/terraform`)

Provisions the full AWS environment using three modules:

| Module | What it creates |
|---|---|
| `vpc` | VPC, public/private subnets, NAT gateways, security groups |
| `eks` | Managed EKS cluster, node groups with auto-scaling |
| `rds` | RDS MySQL (Multi-AZ), subnet group, parameter group |

Remote state in S3 with DynamoDB locking (`foodie-terraform-state` bucket, `foodie-terraform-locks` table).

```bash
cd infra/terraform
terraform init
terraform plan -var-file=environments/prod.tfvars
terraform apply -var-file=environments/prod.tfvars
```

### Helm (`infra/helm/foddie`)

All services are deployed from a single chart. Per-service configuration (port, replicas, resource requests/limits, env vars) lives in `Values.yaml`.

```bash
helm upgrade --install foodie infra/helm/foddie \
  --namespace foodie \
  --create-namespace \
  --set global.imageTag=<git-sha> \
  --set global.registry=<aws-account-id>.dkr.ecr.ap-south-1.amazonaws.com \
  --values infra/helm/foddie/Values.yaml \
  --wait --timeout 5m
```

Rolling update strategy: `maxUnavailable: 0`, `maxSurge: 1` — zero downtime.  
Probes: `/actuator/health/liveness` (after 60s) and `/actuator/health/readiness` (after 30s).

#### Razorpay secrets in Kubernetes

```bash
kubectl create secret generic razorpay-secret \
  --namespace foodie \
  --from-literal=RAZORPAY_KEY_ID=rzp_test_... \
  --from-literal=RAZORPAY_KEY_SECRET=... \
  --from-literal=RAZORPAY_WEBHOOK_SECRET=...
```

Reference the secret in production Helm override values via `envFrom.secretRef`.

### Docker (multi-stage builds)

Every service's `Dockerfile` uses a two-stage build:
1. **Build stage** — Maven + full JDK, compiles the JAR
2. **Runtime stage** — Eclipse Temurin JRE 21 only (no compiler, smaller attack surface)

Container hardening: non-root user, read-only filesystem mounts, ZGC with `-XX:MaxRAMPercentage=75.0` and `-XX:+UseContainerSupport`.

### CI/CD (`.github/workflows`)

**CI (`Ci.yml`)** — triggers on push to `main` or `develop`, PR to `main`:

1. Matrix build — each service compiled and tested in parallel
2. Maven package
3. `docker build` + push to Amazon ECR (tagged with Git SHA and `latest`)

**CD (`Cd.yml`)** — triggers on CI success on `main`:

1. `aws eks update-kubeconfig`
2. `helm upgrade --install` with the Git SHA image tag
3. `kubectl rollout status` for every deployment
4. Failure notification step

---

## Observability

### Distributed tracing

OpenTelemetry SDK + OTLP exporter → Jaeger. Trace ID propagates from the API Gateway through every downstream service **and across Kafka message headers** — a single trace spans the entire Saga.

```
Gateway → order-service → [Kafka] → payment-service → [Kafka] → delivery-service
                ↓                          ↓
         outbox_poller               outbox_poller
```

Dead-letter records also capture the `correlation_id` at DLT arrival time, so a replayed message can be cross-referenced in Jaeger against the original trace.

Jaeger UI: http://localhost:16686 (add Jaeger container to docker-compose for local use).

### Metrics

Micrometer + Prometheus scraping `/actuator/prometheus` on every service. Key metrics:

| Metric | Alert threshold |
|---|---|
| Kafka consumer lag | > 1000 messages |
| Outbox PENDING row count | > 100 (poller may be falling behind) |
| Outbox FAILED row count | > 0 (critical — event stuck) |
| Dead-letter PENDING count | > 0 (operator action needed) |
| P95 API latency | > 200 ms |
| Resilience4j circuit breaker state | `OPEN` |
| JVM heap usage | > 80% |

### Logging

Logback with `logstash-logback-encoder` — every service emits structured JSON compatible with ELK. Trace ID and span ID are injected automatically by the OTel bridge. All dead-letter events are logged at `ERROR` level with `topic`, `key`, and `aggregateId` for alerting.

```properties
logging.level.com.foodie=DEBUG    # application code
logging.level.org.springframework=INFO
logging.level.org.apache.kafka=WARN
```

---

## Security

### JWT flow

1. Client POSTs credentials to `POST /api/auth/login` → auth-service returns a signed JWT (HS256).
2. Client sends `Authorization: Bearer <token>` on every subsequent request.
3. `gateway-service` validates the token signature and expiry via `JwtAuthenticationGatewayFilterFactory`.
4. On success, gateway injects `X-User-Email` and `X-User-Id` headers — downstream services trust these without re-validating the token.
5. JWT validation results are cached in Redis to avoid re-parsing on every request.

### Razorpay webhook verification

`RazorpayWebhookController` verifies every incoming webhook using HMAC-SHA256:

```java
// Razorpay sends X-Razorpay-Signature header
// We recompute HMAC(webhook body, RAZORPAY_WEBHOOK_SECRET) and compare
```

Replayed or tampered webhooks are rejected with 401 before any business logic runs.

### Internal API protection

All dead-letter operator endpoints live under `/internal/**`. The gateway blocks every `/internal/**` request with `HTTP 403` before it reaches any downstream service. Operator tooling must access services directly via:

```bash
# kubectl port-forward (local debugging)
kubectl port-forward svc/order-service 8082:8082 -n foodie

# Then reach the internal API
curl http://localhost:8082/internal/dead-letters/pending
```

### Secrets management

- Local dev: environment variables (`RAZORPAY_KEY_ID`, etc.)
- Kubernetes: `kubectl create secret generic` → mounted via `envFrom.secretRef`
- Never committed to the repository — `.gitignore` excludes `*.env` and `*secret*` files

---

## API reference

All routes go through the API Gateway at `http://localhost:8080`. The gateway validates the JWT and injects `X-User-Email` and `X-User-Id` headers before forwarding.

A full Postman collection is included: **`Foodie-App.postman_collection.json`**.

### Auth (`/api/auth`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | None | Register new user, returns JWT |
| POST | `/api/auth/login` | None | Login, returns JWT |
| GET | `/api/auth/me` | Bearer JWT | Returns claims from current token |

### Orders (`/api/orders`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/orders` | Bearer JWT | Place a new order → triggers Saga via Outbox |
| GET | `/api/orders/{orderUuid}` | Bearer JWT | Get order by UUID (ownership enforced) |
| GET | `/api/orders/id/{id}` | Bearer JWT | Get order by DB id (ownership enforced) |
| GET | `/api/orders/customer/{email}` | Bearer JWT | List all orders for customer |
| GET | `/api/orders/customer/{email}/paged` | Bearer JWT | Paginated + sorted + searchable orders |
| POST | `/api/orders/reorder` | Bearer JWT | Reorder — creates new order and fires Saga |
| DELETE | `/api/orders/{orderUuid}` | Bearer JWT | Delete order |

### Payments (`/api/payments`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/payments` | Bearer JWT | Manual payment (testing / COD) |
| POST | `/api/payments/create` | Bearer JWT | Create Razorpay order (returns razorKey + orderId) |
| POST | `/api/payments/verify` | Bearer JWT | Verify Razorpay signature, mark SUCCESS → Outbox queues PaymentCompletedEvent |
| GET | `/api/payments/order/{orderUuid}` | Bearer JWT | Get payment by order UUID |
| GET | `/api/payments/uuid/{paymentUuid}` | Bearer JWT | Get payment by payment UUID |
| GET | `/api/payments/{id}` | Bearer JWT | Get payment by DB id |
| PUT | `/api/payments/{paymentUuid}` | Bearer JWT | Update payment status |
| POST | `/webhooks/razorpay` | HMAC-SHA256 | Razorpay webhook (signature verified) |

### Deliveries (`/api/deliveries`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/deliveries` | Bearer JWT | Manual delivery assignment |
| GET | `/api/deliveries` | Bearer JWT | List deliveries |
| GET | `/api/deliveries/{orderUuid}` | Bearer JWT | Get delivery by order UUID |
| PUT | `/api/deliveries/{id}/status` | Bearer JWT | Update status (ASSIGNED → PICKED\_UP → ON\_THE\_WAY → DELIVERED) |
| POST | `/api/deliveries/partners` | Bearer JWT | Register delivery partner |
| GET | `/api/deliveries/partners` | Bearer JWT | List delivery partners |

### Restaurants (`/api/restaurants`, `/api/menu-items`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/restaurants` | None | List all restaurants |
| POST | `/api/restaurants` | None | Create restaurant |
| GET | `/api/restaurants/{id}` | None | Get restaurant by id |
| DELETE | `/api/restaurants/{id}` | None | Delete restaurant |
| GET | `/api/restaurants/search?name=&address=` | None | Fuzzy full-text search with city filter (Elasticsearch) |
| GET | `/api/restaurants/filter?cuisine=` | None | Filter by cuisine type |
| GET | `/api/menu-items` | None | List all menu items |
| POST | `/api/menu-items` | None | Create menu item |

### Cart (`/api/cart`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/cart/{userId}` | Bearer JWT | Get cart for user |
| POST | `/api/cart/{userId}/items` | Bearer JWT | Add item to cart |
| DELETE | `/api/cart/{userId}/items/{itemId}` | Bearer JWT | Remove item from cart |
| DELETE | `/api/cart/{userId}` | Bearer JWT | Clear cart |

### Users (`/api/users`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/users/profile` | Bearer JWT | Get own profile |
| PUT | `/api/users/profile` | Bearer JWT | Update profile |
| GET | `/api/users/{id}` | Bearer JWT | Get user by id |

### Dead-letter operator API (internal — not routed through gateway)

| Method | Path | Service | Description |
|---|---|---|---|
| GET | `/internal/dead-letters` | order / payment / delivery / notification | List all dead letters |
| GET | `/internal/dead-letters/pending` | order / payment / delivery / notification | List PENDING only |
| POST | `/internal/dead-letters/{id}/replay` | order / payment / delivery / notification | Replay — re-publishes to original topic |
| POST | `/internal/dead-letters/{id}/ignore` | order / payment / delivery / notification | Mark IGNORED (with optional `?reason=`) |

> **Note:** `id` is a `Long` for MySQL-backed services (order, payment) and a `String` UUID for MongoDB/Redis-backed services (delivery, notification).

### WebSocket

Connect to `ws://localhost:8080/ws` (STOMP). Subscribe to `/topic/orders/{orderUuid}` to receive real-time `OrderUpdatedEvent` and `DeliveryEvent` notifications.

---

## Project structure

```
foodie/
├── common-events/                  # Shared Kafka event DTOs (pure Java + Lombok + Jackson)
│   └── src/main/java/com/foodie/common/
│       ├── events/
│       │   ├── OrderCreatedEvent.java
│       │   ├── OrderUpdatedEvent.java
│       │   ├── PaymentCompletedEvent.java
│       │   ├── PaymentFailedEvent.java
│       │   ├── DeliveryEvent.java
│       │   └── DeadLetterEvent.java        # Dead-letter metadata DTO
│       └── correlation/CorrelationContext.java
│
├── order-service/                  # Port 8082 | MySQL
│   └── src/main/java/com/foodie/order_service/
│       ├── controller/OrderController.java
│       ├── service/OrderService.java
│       ├── model/Order.java
│       ├── outbox/                         # Outbox Pattern
│       │   ├── OutboxEvent.java
│       │   ├── OutboxEventService.java
│       │   ├── OutboxPoller.java
│       │   └── OutboxCleanupScheduler.java
│       ├── idempotency/                    # Consumer deduplication
│       │   ├── ProcessedEvent.java
│       │   ├── IdempotencyService.java
│       │   └── IdempotencyCleanupScheduler.java
│       ├── deadletter/                     # Dead-letter replay safety
│       │   ├── DeadLetter.java             # JPA entity (dead_letters table)
│       │   ├── DeadLetterRepository.java
│       │   ├── DeadLetterService.java      # store / replay / ignore
│       │   ├── DeadLetterController.java   # GET /pending, POST /{id}/replay, POST /{id}/ignore
│       │   └── DltHandlerSupport.java      # JSON serialisation helpers for @DltHandler
│       ├── listener/
│       │   ├── PaymentEventListener.java   # @RetryableTopic + replay-safe @DltHandler
│       │   ├── PaymentFailedEventListener.java
│       │   └── DeliveryEventListener.java
│       └── resilience/ResilientKafkaPublisher.java
│
├── payment-service/                # Port 8083 | MySQL
│   └── src/main/java/com/foodie/payment_service/
│       ├── controller/
│       │   ├── PaymentController.java
│       │   └── RazorpayWebhookController.java
│       ├── service/PaymentService.java
│       ├── outbox/                         # Outbox Pattern
│       ├── idempotency/
│       ├── deadletter/                     # Dead-letter replay safety (MySQL)
│       │   ├── DeadLetter.java
│       │   ├── DeadLetterRepository.java
│       │   ├── DeadLetterService.java
│       │   └── DeadLetterController.java
│       ├── listener/PaymentListener.java   # Handles order-created, replay-safe @DltHandler
│       └── resilience/ResilientRazorpayService.java
│
├── delivery-service/               # Port 8086 | MongoDB
│   └── src/main/java/com/foodie/delivery_service/
│       ├── controller/DeliveryController.java
│       ├── service/DeliveryService.java
│       ├── outbox/                         # Outbox Pattern (MongoDB)
│       ├── idempotency/
│       ├── deadletter/                     # Dead-letter replay safety (MongoDB)
│       │   ├── DeadLetterDocument.java     # @Document with compound unique index
│       │   ├── DeadLetterRepository.java
│       │   ├── DeadLetterService.java
│       │   └── DeadLetterController.java
│       ├── listener/PaymentEventListener.java   # @RetryableTopic + replay-safe @DltHandler
│       └── resilience/ResilientOrderServiceClient.java
│
├── restaurant-service/             # Port 8084 | MongoDB + Elasticsearch
│   └── src/main/java/com/foodie/restaurant_service/
│       ├── controller/
│       ├── service/
│       ├── model/
│       ├── repository/
│       │   ├── RestaurantRepository.java       # MongoDB
│       │   └── RestaurantSearchRepository.java # Elasticsearch
│       └── sync/DataSyncRunner.java            # Warms Elasticsearch on startup
│
├── notification-service/           # Port 8087 | Redis
│   └── src/main/java/com/foodie/notification_service/
│       ├── config/
│       │   ├── KafkaConsumerConfig.java    # Two factories: generic + typed OrderUpdatedEvent
│       │   ├── KafkaProducerConfig.java    # KafkaTemplate for dead-letter replay
│       │   └── WebSocketConfig.java
│       ├── listener/OrderEventListener.java    # @RetryableTopic on both listeners + replay-safe @DltHandler
│       ├── service/NotificationService.java
│       ├── idempotency/IdempotencyService.java # Redis SETNX
│       └── deadletter/                     # Dead-letter replay safety (Redis)
│           ├── DeadLetterEntry.java         # In-memory DTO stored as Redis JSON hash
│           ├── DeadLetterService.java       # store / replay / ignore via Redis
│           └── DeadLetterController.java
│
├── gateway-service/                # Port 8080 | Redis
│   └── src/main/java/com/foodie/gateway_service/
│       ├── filter/JwtAuthenticationGatewayFilterFactory.java
│       ├── util/JwtUtil.java
│       └── controller/FallbackController.java
│   └── src/main/resources/
│       └── application.properties          # Route 99: blocks /internal/** → 403
│
├── auth-service/                   # Port 8081 | MySQL
├── user-service/                   # Port 8085 | MySQL
├── cart-service/                   # Port 8088 | Redis
├── discovery-service/              # Port 8761 | Eureka
├── config-service/                 # Port 8888 | Spring Cloud Config
│
├── infra/
│   ├── terraform/                  # VPC, EKS, RDS modules + S3 remote state
│   └── helm/foddie/                # Single Helm chart for all services
│
├── .github/workflows/
│   ├── Ci.yml                      # Matrix build → test → Docker → ECR
│   └── Cd.yml                      # Helm upgrade on EKS after CI passes
│
├── docker-compose.yml              # Full local stack (profiles: infra | services | all)
├── pom.xml                         # Parent POM — aggregates all modules
├── Foodie-App.postman_collection.json
└── kafka-reset.bat                 # Resets Kafka topics for local dev
```

---

## Key design decisions

**Why Outbox Pattern over direct Kafka send?**  
Direct Kafka sends create a dual-write window. The Outbox Pattern atomically couples the business state change to the event intent using the same DB transaction. The message relay (OutboxPoller) is a best-effort async process — no event is ever silently lost due to Kafka unavailability or a service crash.

**Why persist dead letters instead of just logging?**  
A bare `@DltHandler` that only logs is not a recovery strategy — it's a silent discard. Any message that exhausts its retries represents a real business failure: an order that won't confirm, a payment cancellation that won't propagate, a notification that won't send. FOODIE persists every dead letter with its full payload so the cause can be investigated, the underlying bug fixed, and the message replayed once the system is healthy. The unique constraint on `(source_topic, original_key)` makes the handler safe to invoke multiple times without creating duplicate rows, even if the DLT itself re-delivers the message.

**Why store dead letters in each service's own database?**  
A centralised dead-letter store would introduce a shared dependency and a cross-service failure mode. Each service owns its failures — order-service stores dead letters in its MySQL database, delivery-service in MongoDB, notification-service in Redis. The operator API is identical across all services; only the backing store differs. This follows the same database-per-service principle that governs the rest of the architecture.

**Why Choreography over Orchestration?**  
No single point of failure. Each service is independently deployable and testable. The trade-off is that the flow is only visible by reading the topics and this README. We compensate with clear topic naming, structured logging with trace IDs, and Jaeger distributed tracing that spans the full Saga.

**Why idempotency via DB table instead of Redis?**  
The claim insert and the business-logic update share the same `@Transactional` boundary. If the business logic rolls back, the deduplication row also rolls back — the next retry processes correctly. A Redis-based approach requires a distributed transaction to achieve this guarantee. (notification-service uses Redis since it has no DB and the cost of a duplicate push notification is low.)

**Why `@RetryableTopic` over a manual `DefaultErrorHandler`?**  
Spring Kafka's `@RetryableTopic` auto-creates the retry and DLT topics, handles back-off scheduling declaratively, and keeps the retry config co-located with the listener. The DLT suffix convention is `-dlt`. Every DLT is now backed by a persistent dead-letter store, so no message is ever silently discarded.

**Why ZGC in the Dockerfile?**  
ZGC keeps pause times under 1 ms regardless of heap size. Combined with virtual threads (`spring.threads.virtual.enabled=true`), every Tomcat request thread, `@Async` method, `@Scheduled` task, and Kafka listener dispatch runs on a lightweight virtual thread — eliminating the fixed thread-pool ceiling and reducing context-switch overhead. `-Djdk.tracePinnedThreads=short` is set in every Dockerfile to surface carrier-thread pinning events during load testing. The gateway is deliberately excluded: it runs on Spring WebFlux/Netty, which is already fully non-blocking and incompatible with the virtual-thread model.

**Why MongoDB for delivery-service instead of MySQL?**  
Delivery documents have irregular shapes (partner data, audit timestamps, status history) and are append-heavy. MongoDB's document model fits naturally. The unique index on `orderUuid` provides the same idempotency guarantee as a MySQL unique constraint.

**Why a shared `common-events` library?**  
All Kafka event POJOs are versioned together. Producers and consumers reference the same class, eliminating serialisation drift. The library has no Spring dependency — it is pure Java + Lombok + Jackson, keeping downstream service classpaths clean.
