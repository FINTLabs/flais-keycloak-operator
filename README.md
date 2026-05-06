# flais-keycloak-operator

A Kotlin-based Kubernetes operator that automatically wires application deployments to Keycloak by injecting a [Wonderwall](https://github.com/nais/wonderwall) authentication sidecar and provisioning the corresponding Keycloak OIDC client.

## Overview

The operator watches for `Application` custom resources. When one is created, it:

1. Provisions a Keycloak OIDC client for the application
2. Injects a Wonderwall sidecar container into the matching Deployment
3. Creates a Kubernetes Service that exposes the sidecar

When the `Application` resource is deleted, the operator reverses the process — removing the sidecar from the Deployment and deleting the Keycloak client.

The operator does **not** own or manage the application Deployment itself. It augments an existing workload.

## Custom Resource: `Application`

**API group/version:** `novari.no/v1alpha1`

### Spec

| Field          | Type   | Required | Description                                                      |
| -------------- | ------ | -------- | ---------------------------------------------------------------- |
| `hostname`     | string | yes      | External hostname for the application (e.g. `myapp.example.com`) |
| `basePath`     | string | yes      | Ingress path prefix (e.g. `beta/my-org`)                         |
| `realm`        | string | yes      | Keycloak realm to manage the OIDC client in                      |
| `upstreamPort` | int    | no       | Port the application container listens on (default: `3000`)      |
| `scope`        | string | no       | OIDC scopes to request (default: `profile`)                      |
| `logLevel`     | string | no       | Wonderwall log level (default: `info`)                           |

### Example

```yaml
apiVersion: novari.no/v1alpha1
kind: Application
metadata:
  name: my-app
spec:
  hostname: myapp.example.com
  basePath: beta/my-org
  realm: fint
```

The `Application` resource must have the same `name` and `namespace` as the Deployment it targets.

### Matching Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app # must match Application metadata.name
  labels:
    app: my-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
        - name: app
          image: my-image:latest
          ports:
            - name: app
              containerPort: 3000
```

## Reconcile Flow

```
Application CR created
  │
  ├─ 1. KeycloakClientDR
  │      Create/update OIDC client in Keycloak
  │
  ├─ 2. WonderwallDeploymentDR  (depends on step 1)
  │      Inject wonderwall sidecar into the existing Deployment
  │
  └─ 3. WonderwallServiceDR     (depends on step 2)
         Create <app-name>-wonderwall Service pointing at port 8080
```

### Keycloak client configuration

The operator creates an OIDC client with:

- `clientId` = `metadata.name`
- Protocol: `openid-connect`
- Public client: `false`
- Standard flow: enabled
- PKCE: `S256`
- Redirect URIs: `https://<hostname>/<basePath>/*`
- Web origins: `+`
- Post-logout redirect URIs: `+`

### Wonderwall sidecar

The injected sidecar container uses image `ghcr.io/nais/wonderwall` and binds to `0.0.0.0:8080`. It proxies traffic to `127.0.0.1:<upstreamPort>` (default `3000`).

Key environment variables set on the sidecar:

| Variable                           | Value                                                               |
| ---------------------------------- | ------------------------------------------------------------------- |
| `WONDERWALL_OPENID_CLIENT_ID`      | `metadata.name`                                                     |
| `WONDERWALL_OPENID_WELL_KNOWN_URL` | `http://<keycloak>/realms/<realm>/.well-known/openid-configuration` |
| `WONDERWALL_INGRESS`               | `https://<hostname>/<basePath>`                                     |
| `WONDERWALL_UPSTREAM_HOST`         | `127.0.0.1:<upstreamPort>`                                          |
| `WONDERWALL_BIND_ADDRESS`          | `0.0.0.0:8080`                                                      |
| `WONDERWALL_AUTO_LOGIN`            | `true`                                                              |
| `WONDERWALL_OPENID_SCOPES`         | `profile,<scope>`                                                   |

### Service

A Service named `<app-name>-wonderwall` is created in the same namespace, with:

- Port `80` → targetPort `http` (the sidecar's `8080`)
- Selector labels copied from the Deployment's `spec.selector.matchLabels`
- Label `app.kubernetes.io/managed-by: application-operator`

## Installation

### Prerequisites

- Kubernetes cluster
- Helm 3
- A running Keycloak instance accessible from the operator pod

### 1. Install CRDs

```bash
helm install flais-keycloak-operator-crd \
  oci://ghcr.io/fintlabs/charts/flais-keycloak-operator-crd
```

### 2. Install the operator

```bash
helm install flais-keycloak-operator \
  oci://ghcr.io/fintlabs/charts/flais-keycloak-operator \
  --set keycloak.baseUrl=https://keycloak.example.com \
  --set keycloak.adminUsername=admin \
  --set keycloak.adminPassword=<secret>
```

### Helm values

| Value                       | Default                            | Description                  |
| --------------------------- | ---------------------------------- | ---------------------------- |
| `image.repository`          | `ghcr.io/fintlabs/ssoerator-2-poc` | Operator image               |
| `image.pullPolicy`          | `IfNotPresent`                     | Image pull policy            |
| `keycloak.baseUrl`          | `http://172.17.0.1:8080`           | Keycloak base URL            |
| `keycloak.adminUsername`    | `admin`                            | Keycloak admin username      |
| `keycloak.adminPassword`    | `admin`                            | Keycloak admin password      |
| `wonderwall.image`          | `ghcr.io/nais/wonderwall:...`      | Wonderwall image to inject   |
| `wonderwall.logLevel`       | `info`                             | Default Wonderwall log level |
| `resources.limits.memory`   | `512Mi`                            | Operator memory limit        |
| `resources.requests.cpu`    | `200m`                             | Operator CPU request         |
| `resources.requests.memory` | `256Mi`                            | Operator memory request      |

## Environment Variables

The operator reads the following environment variables at runtime (injected by the Helm chart):

### Required

| Variable                  | Description                       |
| ------------------------- | --------------------------------- |
| `KEYCLOAK_BASE_URL`       | Base URL of the Keycloak instance |
| `KEYCLOAK_ADMIN_USERNAME` | Admin username                    |
| `KEYCLOAK_ADMIN_PASSWORD` | Admin password                    |

### Optional

| Variable                   | Default     | Description                     |
| -------------------------- | ----------- | ------------------------------- |
| `KEYCLOAK_ADMIN_REALM`     | `master`    | Realm used for admin API access |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `admin-cli` | Client ID for admin API access  |

## HTTP Endpoints

The operator exposes an HTTP server on port `8080`:

| Endpoint       | Description                                      |
| -------------- | ------------------------------------------------ |
| `GET /metrics` | Prometheus metrics scrape endpoint               |
| `GET /health`  | Returns `200 OK` when the operator is running    |
| `GET /ready`   | Returns `200 READY` when the operator is running |

These are used as the liveness and readiness probes in the Helm deployment.

## Local Development

### Prerequisites

- JDK 21+
- Docker / Docker Compose
- A local Keycloak instance (provided via Docker Compose)

### Start the local stack

```bash
docker compose -f docker-compose.dev.yaml up
```

This starts Keycloak pre-configured with a `fint` realm.

### Run the operator locally

```bash
./gradlew runDev
```

### Run tests

```bash
./gradlew test
```

The test compose file (`docker-compose.test.yaml`) is used automatically during integration tests.

### Generate CRDs

```bash
./gradlew generateCrds
```

Generated CRD manifests are written to `charts/flais-keycloak-operator-crd/`.

### Install to a local cluster

```bash
./gradlew installOperator
```

## Technology Stack

| Component            | Library                       |
| -------------------- | ----------------------------- |
| Language             | Kotlin 2.x                    |
| Kubernetes client    | Fabric8 Kubernetes Client 7.x |
| Operator framework   | Java Operator SDK (JOSDK)     |
| Dependency injection | Koin 4.x                      |
| HTTP server          | Http4k + Jetty                |
| Metrics              | Micrometer + Prometheus       |
| CRD generation       | Fabric8 CRD Generator v2      |

## Architecture Notes

The operator introduces a thin layer on top of JOSDK to support Koin-managed dependent resources:

- `@Workflow` / `@Dependent` / `@DependentRef` — custom annotations that declare the dependency graph on reconciler classes
- `KoinDependentResourceFactory` — resolves dependent resource instances from the Koin container
- `OperatorConfiguration` — translates the annotations into JOSDK `DependentResourceSpec`s at startup, wiring ready conditions and reconcile conditions automatically

This keeps reconciler classes compact while allowing dependent resources to use constructor injection.
