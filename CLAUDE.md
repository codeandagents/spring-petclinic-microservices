# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Context

This is a fork of the upstream Spring PetClinic Microservices used in the "Code & Agents" video series. Per-episode changes live on `ep01-*`, `ep02-*`, ... branches (tagged `ep01-final`, ...); everything else is upstream and should be kept as-is. Stack: Java 17, Spring Boot 4.0.1, Spring Cloud 2025.1.0, Spring AI.

## Commands

Maven multi-module build; use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

```bash
./mvnw -B package                          # build + test everything (what CI runs)
./mvnw test -pl spring-petclinic-customers-service                 # one module
./mvnw test -pl spring-petclinic-customers-service -Dtest=PetResourceTest            # one test class
./mvnw test -pl spring-petclinic-customers-service -Dtest=PetResourceTest#methodName # one test method
./mvnw spring-boot:run -pl spring-petclinic-config-server          # run a single service
./mvnw clean install -P buildDocker        # build Docker images (add -Dcontainer.executable=podman / -Dcontainer.platform=linux/arm64)
docker compose up                          # start full stack from built images
./scripts/run_all.sh [--chaos-monkey]      # docker-compose infra + `java -jar` apps; stop with ./scripts/stop_all.sh
```

There is no separate lint step; formatting follows `.editorconfig`. PRs must follow `.github/PULL_REQUEST_TEMPLATE.md` (checked by a workflow).

## Architecture

Eight modules (see root `pom.xml`). Services register with Eureka and are reached via `lb://<service-name>`.

- **config-server** (8888) – Spring Cloud Config. Serves config from the external git repo `spring-petclinic-microservices-config` by default; use the `native` profile with `GIT_REPO=/path` to serve a local checkout. Each service's own `application.yml` only holds bootstrap settings (`spring.config.import: optional:configserver:...`); most real settings (ports, datasource, etc.) come from that external repo.
- **discovery-server** (8761) – Eureka.
- **api-gateway** (8080) – Spring Cloud Gateway (WebFlux) plus the AngularJS frontend (`src/main/resources/static`). Routes `/api/{customer,vet,visit,genai}/**` to the matching service with `StripPrefix=2`, with default CircuitBreaker (fallback `/fallback`) and Retry filters. It also aggregates data across services via `CustomersServiceClient` / `VisitsServiceClient` into DTOs like `OwnerDetails` (`api/application`, `api/dto`, `api/boundary/web`).
- **customers-service, vets-service, visits-service** – REST services (`@RestController` + Spring Data JPA repositories), each with its own database. Default is in-memory HSQLDB; the `mysql` Spring profile switches to MySQL (schema/data under `src/main/resources/db/{hsqldb,mysql}`). They start on random ports, so check Eureka for the port.
- **genai-service** – Spring AI chat assistant (`PetclinicChatClient`, `PetclinicTools` for tool calling into other services, `AIDataProvider`, `VectorStoreController`). Uses OpenAI by default (`OPENAI_API_KEY`, falls back to the rate-limited `demo` key); to use Azure OpenAI, swap the starter in `spring-petclinic-genai-service/pom.xml` and set `AZURE_OPENAI_KEY` / `AZURE_OPENAI_ENDPOINT`.
- **admin-server** (9090) – Spring Boot Admin.

Startup order matters: config-server and discovery-server must be up before the other services. Under the `docker` profile, services import config from `http://config-server:8888`. Observability: Zipkin (9411), Prometheus (9091), Grafana (3030), Prometheus/Grafana config is under `docker/`; all are wired up in `docker-compose.yml`. Chaos Monkey is bundled in the services; see `scripts/chaos/README.md`.

## Tests

Tests are sparse and live in each module's `src/test`: web-layer tests (`*ResourceTest`, `ApiGatewayControllerTest`), a mock-server-based client integration test (`VisitsServiceClientIntegrationTest`) in api-gateway, and context-load tests for config/discovery/gateway. Service test config is in `src/test/resources/application-test.yml`.
