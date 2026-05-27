# Foodie V2

Foodie V2 is a microservices food ordering platform with a React frontend,
Spring Boot services, Kafka event flows, MySQL, MongoDB, Redis, Elasticsearch,
and an observability stack for traces and metrics.

## Services

| Component | Port | Purpose | Data/dependencies |
| --- | ---: | --- | --- |
| frontend | 80 in container | React/Vite UI served by Nginx | gateway-service |
| gateway-service | 8080 | Public API and WebSocket gateway | downstream services |
| auth-service | 8081 | Registration, login, JWT issuing | MySQL, user-service |
| user-service | 8085 | User profile and internal auth lookup | MySQL |
| restaurant-service | 8084 | Restaurants, menu, search | MongoDB, Elasticsearch |
| cart-service | 8088 | Cart state | MongoDB |
| order-service | 8082 | Orders and order saga state | MySQL, Kafka |
| payment-service | 8083 | Payment creation, verification, events | MySQL, Kafka, Razorpay |
| delivery-service | 8086 | Delivery assignment and delivery events | MongoDB, Kafka, order-service |
| notification-service | 8087 | Notifications and WebSocket updates | Kafka, Redis |
| common-events | n/a | Shared event DTO library | Maven dependency |

## Requirements

- Java 21
- Maven 3.9+
- Node.js 22+ and npm
- Docker or Docker Desktop
- kubectl for Kubernetes
- Helm 3 for Kubernetes app deployment

On Windows PowerShell, use `npm.cmd` if `npm` is blocked by the script execution
policy.

## Local Docker Compose

Build the backend jars first because the service Docker images copy packaged
jars from each service target directory.

```powershell
mvn -B -ntp -DskipTests package
docker compose up --build
```

Useful local URLs:

- Frontend: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- Kafka UI: `http://localhost:8090`
- Jaeger UI: `http://localhost:16686`

Stop everything with:

```powershell
docker compose down
```

## Local Development

Backend checks:

```powershell
mvn -B -ntp test
```

Frontend checks:

```powershell
cd Foodie-App-Frontend
npm.cmd ci
npm.cmd run build
```

## Kubernetes

Kubernetes is split into two layers:

- `infra/k8s`: namespace, local platform dependencies, secrets, config maps, and observability.
- `infra/helm/foddie`: Helm chart for the frontend and all application services.

The Kubernetes base uses `emptyDir` volumes so it is easy to run locally. Replace
those with managed databases, persistent volumes, and external secret management
before using this in production.

### 1. Build application images

```powershell
mvn -B -ntp -DskipTests package

$services = @(
  "gateway-service",
  "auth-service",
  "user-service",
  "restaurant-service",
  "order-service",
  "payment-service",
  "delivery-service",
  "notification-service",
  "cart-service"
)

foreach ($service in $services) {
  docker build `
    -f Dockerfile.service `
    --build-arg SERVICE_DIR=$service `
    -t "foodie-$service:local" `
    .
}

docker build -t foodie-frontend:local .\Foodie-App-Frontend
```

If you use kind or minikube, load the local images into the cluster after
building them. Docker Desktop Kubernetes can usually see locally built images
without this step.

### 2. Install autoscaling prerequisites

KEDA is required because the chart creates Kafka lag based `ScaledObject`
resources. Metrics Server is needed for HPA CPU and memory metrics.

```powershell
helm repo add kedacore https://kedacore.github.io/charts
helm repo update
helm upgrade --install keda kedacore/keda --namespace keda --create-namespace

kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

### 3. Apply the platform layer

```powershell
kubectl apply -k .\infra\k8s
kubectl get pods -n foodie
```

This starts MySQL, MongoDB, Redis, Elasticsearch, Kafka, Prometheus, Jaeger,
Tempo, Grafana, and the OpenTelemetry Collector.

### 4. Deploy the application chart

```powershell
helm upgrade --install foodie .\infra\helm\foddie `
  --namespace foodie `
  -f .\infra\helm\foddie\Values.yaml `
  --set global.imageTag=local
```

Access the app:

```powershell
kubectl port-forward -n foodie svc/frontend 3000:80
```

Then open `http://localhost:3000`.

Useful observability forwards:

```powershell
kubectl port-forward -n foodie svc/grafana 3001:3000
kubectl port-forward -n foodie svc/jaeger-ui 16686:16686
kubectl port-forward -n foodie svc/prometheus 9090:9090
```

Grafana defaults for the local manifest are `admin` / `foodie-grafana-secret`.

## Configuration And Secrets

Kubernetes config lives in `infra/k8s/configmaps.yaml` and
`infra/k8s/secrets.yaml`.

The committed secrets are local development placeholders only:

- MySQL uses `root` / `root`.
- Razorpay values are dummy local values.
- JWT and internal service secrets are local values and must be replaced outside development.

The auth, gateway, and user services now read JWT/internal shared secrets from
environment-backed Spring properties, so Kubernetes Secrets can control those
values without code changes.

## CI Workflow

GitHub Actions workflow: `.github/workflows/ci.yml`.

It runs:

- Maven `verify` for the backend reactor.
- `npm ci` and Vite build for the frontend.
- Docker builds for all service images and the frontend image.
- Helm lint/template validation.
- Kustomize render validation for `infra/k8s`.

## Repository Hygiene

Build outputs, local Maven caches, logs, frontend dependencies, and Terraform
state are ignored in `.gitignore`. The repo currently has tracked `.m2`
artifacts, which can trigger Git LFS clean errors during `git status`.

After confirming with the team, clean the index without deleting local files:

```powershell
git rm --cached -r .m2
git rm --cached -r */target
```

Then commit the cleanup with the new `.gitignore`.

## Troubleshooting

- `ImagePullBackOff`: the cluster cannot see the local `foodie-*:local` images. Load them into kind/minikube or push to a registry and set `global.registry` plus `global.imageTag`.
- `ScaledObject` errors: install KEDA before the Helm chart.
- HPA shows unknown metrics: install Metrics Server.
- Elasticsearch will not start on Linux nodes: set `vm.max_map_count=262144` on the node.
- Frontend API calls fail: check `svc/gateway-service`, gateway logs, and the downstream service pod readiness.
