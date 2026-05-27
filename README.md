# Foodie V2

Foodie V2 is a production-style distributed food ordering platform built with Spring Boot microservices, Kafka event choreography, transactional outbox, idempotent consumers, Redis caching, Kubernetes deployment automation, and end-to-end observability.

The project focuses on real backend engineering concerns:

- Distributed system reliability
- Event-driven architecture
- Saga-style workflows
- Kafka retry + DLQ handling
- Idempotent event processing
- Transactional outbox pattern
- OpenTelemetry distributed tracing
- CI/CD automation
- Containerized cloud-native deployment

---

# Architecture Overview

## Core Architecture Patterns

| Pattern | Implementation |
|---|---|
| Microservices | Spring Boot services with independent persistence |
| Event-Driven Architecture | Kafka-based async workflows |
| Transactional Outbox | Reliable event publishing without dual-write inconsistency |
| Idempotent Consumers | Duplicate Kafka replay protection |
| Retry + DLQ | RetryableTopic + dead-letter replay handling |
| Saga/Event Choreography | Cross-service order lifecycle orchestration |
| CQRS-style Read Optimization | Redis-backed read acceleration |
| Distributed Tracing | OpenTelemetry + Jaeger |
| Horizontal Scaling | Kubernetes + KEDA autoscaling |

---

# System Flow

```text
Frontend
   ↓
API Gateway
   ↓
Order Service
   ↓
Kafka Events
   ↓
Payment Service
   ↓
Delivery Service
   ↓
Notification Service
```

Services communicate asynchronously using Kafka events to reduce coupling and improve resiliency.

---

# Tech Stack

## Backend

- Java 21
- Spring Boot 3
- Spring Cloud
- Spring Security + JWT
- Spring Data JPA
- Spring Kafka
- Maven

## Frontend

- React
- Vite
- Axios
- Nginx

## Databases

- MySQL
- MongoDB
- Redis
- Elasticsearch

## DevOps & Infrastructure

- Docker
- Kubernetes
- Helm
- KEDA
- GitHub Actions
- Testcontainers

## Observability

- OpenTelemetry
- Prometheus
- Grafana
- Jaeger
- Tempo

---

# Services

| Service | Responsibility | Storage |
|---|---|---|
| gateway-service | API gateway + routing | - |
| auth-service | JWT auth + login | MySQL |
| user-service | User profile management | MySQL |
| restaurant-service | Restaurants + menu search | MongoDB + Elasticsearch |
| cart-service | Shopping cart state | MongoDB |
| order-service | Order lifecycle + saga state | MySQL |
| payment-service | Payment processing + retries | MySQL |
| delivery-service | Delivery assignment workflow | MongoDB |
| notification-service | Real-time notifications | Redis |
| common-events | Shared Kafka DTO contracts | Maven module |

---

# Reliability Features

## Transactional Outbox

Foodie V2 uses the Transactional Outbox Pattern to avoid dual-write inconsistencies between database commits and Kafka publishing.

```text
DB Commit
   ↓
Outbox Event Persisted
   ↓
Async Publisher
   ↓
Kafka Publish
```

This guarantees eventual consistency even during broker outages.

---

## Idempotent Kafka Consumers

Kafka consumers maintain processed-event tracking to suppress duplicate event replays.

Scenarios covered:

- Broker redelivery
- Consumer restart replay
- DLQ replay duplication
- At-least-once delivery semantics

---

## Retry + Dead Letter Queue Handling

Kafka listeners use retry topics and DLQ recovery flows.

Implemented features:

- Exponential retry backoff
- Retry topic routing
- Dead-letter persistence
- Manual replay support
- Replay idempotency protection

---

# Observability

The platform includes end-to-end distributed tracing.

Implemented:

- Correlation ID propagation
- OpenTelemetry instrumentation
- Jaeger trace visualization
- Prometheus metrics
- Grafana dashboards
- Kafka trace propagation

---

# Testing Strategy

The project contains:

| Test Type | Coverage |
|---|---|
| Unit Tests | Service logic |
| Integration Tests | Kafka + DB flows |
| Testcontainers | Real infrastructure validation |
| Replay Tests | Duplicate event suppression |
| Retry Tests | Kafka retry behavior |
| DLQ Tests | Replay + recovery flows |

Integration tests validate:

- Transactional consistency
- Kafka replay safety
- Outbox correctness
- Eventual consistency
- Retry recovery semantics

---

# Local Development

## Requirements

- Java 21
- Maven 3.9+
- Node.js 22+
- Docker Desktop
- kubectl
- Helm 3

---

## Run Locally

### Backend

```bash
mvn clean verify
```

### Frontend

```bash
cd Foodie-App-Frontend
npm install
npm run dev
```

---

# Docker Compose

```bash
mvn -DskipTests package

docker compose up --build
```

Useful URLs:

| Component | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Gateway | http://localhost:8080 |
| Kafka UI | http://localhost:8090 |
| Jaeger | http://localhost:16686 |
| Grafana | http://localhost:3001 |

---

# Kubernetes Deployment

Infrastructure includes:

- Helm deployment charts
- KEDA autoscaling
- OpenTelemetry collector
- Prometheus stack
- Kafka deployment
- Elasticsearch deployment
- ConfigMap + Secret management

Deploy:

```bash
kubectl apply -k infra/k8s

helm upgrade --install foodie ./infra/helm/foddie \
  --namespace foodie \
  -f ./infra/helm/foddie/Values.yaml
```

---

# CI/CD Pipeline

GitHub Actions pipeline performs:

- Maven verify
- Frontend Vite build
- Docker image builds
- Integration test execution
- Helm validation
- Kubernetes manifest validation

Pipeline includes multi-service reactor builds with caching optimization.

---

# Performance Engineering

Optimizations implemented:

- Redis caching for hot reads
- Async Kafka workflows
- Virtual-thread Kafka consumers
- Reduced synchronous coupling
- Elasticsearch-based search
- Distributed trace correlation
- Container-aware JVM tuning

---

# Security

- JWT authentication
- Internal service token validation
- Spring Security filters
- Environment-backed secrets
- Gateway-level request routing

---

# Production Notes

This repository is optimized primarily for local distributed-system experimentation and backend engineering demonstrations.

Production deployment would additionally require:

- Managed databases
- External secret management
- Persistent storage
- Multi-node Kafka
- Backup strategy
- Ingress hardening
- TLS termination
- Production monitoring policies

---

# Why This Project Matters

Most tutorial microservice projects stop at CRUD APIs.

Foodie V2 focuses on the hard parts of backend engineering:

- Distributed consistency
- Failure recovery
- Event replay safety
- Async workflow orchestration
- Observability
- Infrastructure automation
- Resilient message processing

The goal of this project is to simulate production-style backend engineering patterns used in scalable cloud-native systems.
