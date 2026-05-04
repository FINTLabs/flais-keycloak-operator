# Operator implementation notes

This document explains how the operator works.

## What this project is

This is a **Kotlin-based Kubernetes operator** built with:

- **Java Operator SDK** for reconcile loops and dependent resource workflows
- **Fabric8 Kubernetes Client** for Kubernetes API access
- **Koin** for dependency injection
- **Http4k + Jetty** for `/metrics`, `/health`, and `/ready`
- **Micrometer + Prometheus** for metrics

The operator defines a custom resource:

- `Application` — the real controller that wires an app deployment to Keycloak and injects a Wonderwall sidecar

## Entry point and runtime

The process starts in `src/main/kotlin/no/novari/Application.kt`.

Startup does three things:

1. Starts Koin modules for the `Application` reconcilers.
2. Starts an HTTP server on port `8080`.
3. Starts the operator.

### HTTP endpoints

The operator exposes:

- `GET /metrics` — Prometheus scrape endpoint
- `GET /health` — returns `200 OK` when the operator runtime is started
- `GET /ready` — returns `200 READY` when the operator runtime is started

These probes are also used in the Helm deployment chart.

## Dependency injection and operator registration

Koin is used to register:

- the Kubernetes client
- the Prometheus registry / metrics adapter
- the operator configuration
- the dependent resources
- the reconcilers themselves

`OperatorConfiguration` extends `BaseConfigurationService` from Java Operator SDK and customizes how workflows are built.

The important part is that this project adds its own `@Workflow` and `@Dependent` annotations, then translates them into Java Operator SDK dependent-resource specs at runtime.

That means the reconciler classes stay compact, while the actual dependency graph is declared via annotations.

## Custom resources

## `Application`

API:

- Group: `novari.no`
- Version: `v1alpha1`
- Kind: `Application`

Spec:

```yaml
spec:
  hostname: samtykke.vigoiks.no
  basePath: beta/rogfk-no
  realm: fint
```

Fields:

- `hostname` — external host for the application
- `basePath` — ingress path prefix
- `realm` — Keycloak realm to manage the client in

Example:

```yaml
apiVersion: novari.no/v1alpha1
kind: Application
metadata:
  name: beta-fint-samtykke-frontend-v2
spec:
  hostname: samtykke.vigoiks.no
  basePath: beta/rogfk-no
  realm: fint
```

## What the `Application` controller does

`ApplicationReconciler` is the main controller.

It declares this workflow:

1. `KeycloakClientDR`
2. `WonderwallDeploymentDR` depends on `KeycloakClientDR`
3. `WonderwallServiceDR` depends on `WonderwallDeploymentDR`

The reconciler itself is intentionally thin:

- on reconcile: it delegates to `reconcileManagedWorkflow()`
- on delete: it removes the Wonderwall sidecar from the target deployment and deletes the Keycloak client

So the real logic lives in the dependent resources.

## Detailed reconcile flow for `Application`

### 1. Keycloak client reconciliation

Handled by `KeycloakClientDR`, which delegates to `KeycloakClientService`.

Behavior:

- Connects to Keycloak using admin credentials from environment variables
- Looks up a client with `clientId == metadata.name`
- Creates it if missing
- Updates it if it already exists

### Keycloak connection env vars

Required:

- `KEYCLOAK_BASE_URL`
- `KEYCLOAK_ADMIN_USERNAME`
- `KEYCLOAK_ADMIN_PASSWORD`

Optional with defaults:

- `KEYCLOAK_ADMIN_REALM` default: `master`
- `KEYCLOAK_ADMIN_CLIENT_ID` default: `admin-cli`

### Resulting Keycloak client shape

The client representation is configured as:

- `clientId = metadata.name`
- `name = metadata.name`
- protocol: `openid-connect`
- enabled: `true`
- public client: `false`
- standard flow enabled: `true`
- direct access grants: `false`
- service accounts: `false`
- full scope allowed: `false`
- redirect URIs: `https://<hostname>/<basePath>/*`
- web origins: `+`
- attributes:
  - `pkce.code.challenge.method = S256`
  - `post.logout.redirect.uris = +`

### Important note

The code sets `WONDERWALL_OPENID_CLIENT_SECRET=public-client`, while the Keycloak client itself is created with `isPublicClient = false`.
That combination looks suspicious and may be a dev-time shortcut or an inconsistency that should be verified in practice.

---

### 2. Wonderwall sidecar injection

Handled by `WonderwallDeploymentDR`.

This resource does **not** create a Deployment.
Instead, it expects an existing Deployment with the **same name and namespace** as the `Application` custom resource.

If the Deployment does not already exist, reconcile fails.

#### Expected target deployment

From the example files, the intended pattern is:

- create a normal app Deployment first
- create an `Application` CR with the same `metadata.name`
- the operator mutates that Deployment by injecting a `wonderwall` container

#### What it injects

The operator ensures there is exactly one container named `wonderwall` at the front of the pod container list.

Image:

- `ghcr.io/nais/wonderwall:2026-02-10-090912-dd200bb`

Port:

- container port `8080`, named `http`

Environment variables:

- `WONDERWALL_OPENID_CLIENT_ID = <application metadata.name>`
- `WONDERWALL_OPENID_CLIENT_SECRET = public-client`
- `WONDERWALL_OPENID_WELL_KNOWN_URL = http://172.17.0.1:8080/realms/<realm>/.well-known/openid-configuration`
- `WONDERWALL_INGRESS = https://<hostname>/<basePath>`
- `WONDERWALL_LOG_LEVEL = debug`
- `WONDERWALL_UPSTREAM_HOST = 127.0.0.1:3000`
- `WONDERWALL_BIND_ADDRESS = 0.0.0.0:8080`
- `WONDERWALL_AUTO_LOGIN = true`
- `WONDERWALL_OPENID_SCOPES = profile,organization`

#### Idempotency

The controller compares the existing `wonderwall` container against the desired definition by checking:

- container name
- image
- env vars
- ports

If it already matches exactly and appears once, it does nothing.
Otherwise it rewrites the container list so the desired sidecar is present exactly once.

#### Ready condition

This dependent resource is considered ready when the Deployment contains a container named `wonderwall`.

---

### 3. Wonderwall Service creation

Handled by `WonderwallServiceDR`.

This one **does** create a Kubernetes Service.

It reads the target Deployment and reuses the Deployment's selector labels.
Then it creates a Service named:

- `<application-name>-wonderwall`

Service properties:

- port `80`
- targetPort `http`
- selector copied from the Deployment's `spec.selector.matchLabels`
- label `app.kubernetes.io/managed-by: application-operator`

This depends on the Wonderwall sidecar step, so the Service is created only after the sidecar step is considered ready.

---

## Delete behavior

When an `Application` resource is deleted, the controller performs cleanup:

1. Removes the `wonderwall` container from the target Deployment, if present
2. Deletes the Keycloak client, if present
3. Returns default delete control to finish CR cleanup

Notably:

- it does **not** delete the main app Deployment
- it does **not** explicitly delete the Service in `cleanup()`
- the Wonderwall Service is managed as a dependent Kubernetes resource, so lifecycle depends on Java Operator SDK dependent-resource handling and owner references / matching behavior

The intended design is clearly “augment an existing workload” rather than “own the full workload”.

## How workflow wiring works internally

Instead of relying only on Java Operator SDK annotations directly, this project introduces its own small workflow layer:

- `@Workflow`
- `@Dependent`
- `@DependentRef`
- `ReadyCondition`
- `ReconcileCondition`
- `KoinDependentResourceFactory`
- `OperatorConfiguration`

### Why it exists

This layer gives the project two conveniences:

1. **Koin-backed dependent resource creation**
   - dependents can be regular Koin beans
   - they can have constructor injection

2. **Annotation-driven dependency graph**
   - reconciler declares dependents once
   - dependency ordering is built at runtime

### What `OperatorConfiguration` does

When the operator loads a reconciler:

- it checks whether the reconciler class has a `@Workflow` annotation
- if yes, it resolves the dependent classes from Koin
- it converts them into Java Operator SDK `DependentResourceSpec`s
- if the dependent implements `ReadyCondition`, that becomes the JOSDK ready condition
- if the dependent implements `ReconcileCondition`, that becomes the reconcile condition
- dependency names from `dependsOn` are resolved from the referenced dependent instances

This is the key custom infrastructure in the repository.

## Deployment model

The repository includes two Helm charts:

- `charts/flais-keycloak-operator` — deploys the operator itself
- `charts/flais-keycloak-operator-crd` — installs the generated CRDs

The operator Deployment exposes port `8080` for metrics / probes.
Readiness and liveness probes hit `/ready` and `/health`.

There is also local dev support via Docker Compose and helper Gradle tasks such as `runDev`.

## Minimal mental model

A concise way to think about the operator:

- `Application` = “attach SSO to an existing Deployment” controller

The `Application` reconcile loop performs three linked actions:

- ensure a Keycloak client exists
- inject a Wonderwall auth sidecar into the matching Deployment
- expose the sidecar through a Service

On deletion it undoes the sidecar injection and removes the Keycloak client.
