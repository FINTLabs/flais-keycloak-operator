# flais-keycloak-operator

A Kotlin-based Kubernetes operator that provisions Keycloak OIDC clients for `FlaisAuthentication` custom resources and exposes the generated Wonderwall configuration as Kubernetes resources.

## Overview

The operator watches `FlaisAuthentication` custom resources in the `novari.no/v1alpha1` API group. For each resource, it:

1. Creates or updates a Kubernetes Secret named `<resource-name>-wonderwall`
2. Creates or updates a Kubernetes ConfigMap named `<resource-name>-wonderwall`
3. Creates or updates a confidential Keycloak OIDC client in the configured realm
4. Synchronizes the Keycloak client secret back into the Wonderwall Secret

When a `FlaisAuthentication` resource is deleted, the operator deletes the corresponding Keycloak client. The Kubernetes Secret and ConfigMap are owned by the `FlaisAuthentication` resource and are garbage-collected by Kubernetes.

The operator does **not** mutate application Deployments, inject sidecars, or create Services. Workloads that use Wonderwall should consume the generated Secret and ConfigMap themselves.

## Custom Resource: `FlaisAuthentication`

**API group/version:** `novari.no/v1alpha1`
**Kind:** `FlaisAuthentication`
**Scope:** Namespaced

### Spec

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `realm` | string | yes | Keycloak realm to manage the OIDC client in. Currently validated to `fint`. |
| `ingress` | array | no | External ingress URLs for the application. Each item contains `host` and optional `path`. Used for Keycloak redirect URIs and `WONDERWALL_INGRESS`. |
| `wonderwall` | object | yes | Wonderwall configuration written to the generated ConfigMap. |

### `ingress[]`

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `host` | string | yes | External hostname, for example `app.example.com`. |
| `path` | string | no | Path prefix below the host. Leave empty for the host root. |

### `wonderwall`

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `upstreamPort` | int | yes | `0` | Port where the application listens. Written as `WONDERWALL_UPSTREAM_PORT`. |
| `autoLogin` | boolean | no | `true` | Written as `WONDERWALL_AUTO_LOGIN`. |
| `scope` | string array | no | `["profile"]` | OIDC scopes written as comma-separated `WONDERWALL_OPENID_SCOPES`. Valid values are `profile` and `organization`; at most two values are allowed. |
| `logLevel` | string | no | `info` | Valid values are `info` and `debug`. Written as `WONDERWALL_LOG_LEVEL`  |

### Example

```yaml
apiVersion: novari.no/v1alpha1
kind: FlaisAuthentication
metadata:
  name: my-app
  namespace: default
spec:
  realm: fint
  ingress:
    - host: myapp.example.com
      path: beta/my-org
  wonderwall:
    upstreamPort: 3000
    autoLogin: true
    scope:
      - profile
      - organization
    logLevel: info
```

## Reconcile Flow

```text
FlaisAuthentication CR created or updated
  │
  ├─ 1. WonderwallSecretDR
  │      Create/update <name>-wonderwall Secret and persist the generated client ID annotation
  │
  ├─ 2. WonderwallConfigMapDR  (depends on step 1)
  │      Create/update <name>-wonderwall ConfigMap with Wonderwall environment variables
  │
  └─ 3. KeycloakClientDR       (depends on steps 1 and 2)
         Create/update Keycloak OIDC client and sync its secret into the Kubernetes Secret
```

## Generated Kubernetes Resources

For a `FlaisAuthentication` named `my-app`, the operator creates resources named `my-app-wonderwall` in the same namespace.

Both resources are labelled with:

```yaml
app.kubernetes.io/managed-by: flais-keycloak-operator
```

Both resources also get an owner reference back to the `FlaisAuthentication` resource.

### Secret

The Secret is an `Opaque` Secret. It stores the generated client ID as an annotation and, after the Keycloak client has been created, stores the Keycloak client secret as data.

Annotation:

| Annotation | Description |
| --- | --- |
| `flais.novari.no/wonderwall-client-id` | Stable generated Keycloak client ID. The value is a UUID. |

Data:

| Key | Description |
| --- | --- |
| `WONDERWALL_OPENID_CLIENT_SECRET` | Base64-encoded Keycloak client secret. |

### ConfigMap

The ConfigMap contains Wonderwall environment variables:

| Key | Value |
| --- | --- |
| `WONDERWALL_OPENID_CLIENT_ID` | Stable generated client ID from the Secret annotation. |
| `WONDERWALL_OPENID_WELL_KNOWN_URL` | `<KEYCLOAK_BASE_URL>/realms/<realm>/.well-known/openid-configuration`. |
| `WONDERWALL_INGRESS` | Comma-separated ingress URLs, for example `https://myapp.example.com/beta/my-org`. |
| `WONDERWALL_UPSTREAM_PORT` | `spec.wonderwall.upstreamPort`. |
| `WONDERWALL_BIND_ADDRESS` | `0.0.0.0:8080`. |
| `WONDERWALL_AUTO_LOGIN` | `spec.wonderwall.autoLogin`. |
| `WONDERWALL_OPENID_SCOPES` | Comma-separated `spec.wonderwall.scope`. |

## Keycloak Client Configuration

The operator creates a confidential OIDC client in `spec.realm`.

| Setting | Value |
| --- | --- |
| Client ID | Generated UUID stored in the Secret annotation. |
| Name | `metadata.name` from the `FlaisAuthentication`. |
| Protocol | `openid-connect`. |
| Enabled | `true`. |
| Public client | `false`. |
| Standard flow | Enabled. |
| Direct access grants | Disabled. |
| Service accounts | Disabled. |
| Full scope allowed | Disabled. |
| PKCE challenge method | `S256`. |
| Redirect URIs | One `https://<host>/<path>/*` entry per `spec.ingress` item. If `path` is empty, the redirect URI is `https://<host>/*`. |
| Web origins | `+`. |
| Post-logout redirect URIs | `+`. |

## Installation

### Prerequisites

- Kubernetes cluster
- Helm 3
- A running Keycloak instance accessible from the operator pod
- Keycloak admin credentials that can create, update, and delete clients in the configured realm

## Environment Variables

The operator reads the following environment variables at runtime.

### Required

| Variable | Description |
| --- | --- |
| `KEYCLOAK_BASE_URL` | Base URL of the Keycloak instance. |
| `KEYCLOAK_ADMIN_USERNAME` | Admin username. |
| `KEYCLOAK_ADMIN_PASSWORD` | Admin password. |

### Optional

| Variable | Default | Description |
| --- | --- | --- |
| `KEYCLOAK_ADMIN_REALM` | `master` | Realm used for admin API access. |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `admin-cli` | Client ID for admin API access. |

## HTTP Endpoints

The operator exposes an HTTP server on port `8080`.

| Endpoint | Description |
| --- | --- |
| `GET /metrics` | Prometheus metrics scrape endpoint. |
| `GET /health` | Returns `200 OK` when the operator is running; otherwise `503 NOT OK`. |
| `GET /ready` | Returns `200 READY` when the operator is running; otherwise `503 NOT READY`. |

These endpoints are used as the liveness and readiness probes in the Helm Deployment.

## Local Development

### Prerequisites

- JDK 21+
- Docker / Docker Compose

### Start the local development environment

```bash
./gradlew runDev
```

`runDev` starts the Docker Compose services for Keycloak and the local k3s cluster, builds the operator image, imports it into k3s, installs the CRD chart, and installs the operator chart.

The local Keycloak instance is imported with the `fint` realm from `config/kc/fint-realm.json`.

The local kubeconfig can be found in `data/kubeconfig/kubeconfig.yaml` - See [Help](help.md) for examples on using

### Stop the local development environment

```bash
./gradlew stopDev
```

### Run tests

```bash
./gradlew test
```

### Run integration tests

```bash
./gradlew integrationTest
```

The integration test suite builds and saves the operator image, starts Keycloak and k3s with Testcontainers, installs the operator, and verifies the generated Kubernetes and Keycloak resources.
