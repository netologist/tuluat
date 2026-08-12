# Tech Debt: SNAPSHOT / Pre-Release Dependency Risk

- **Status:** Accepted (PoC trade-off)
- **Date:** 2026-08-12
- **Severity:** High
- **Module:** Root `pom.xml`

## Current State

The project depends on multiple pre-release artifacts as declared in the root POM:

```xml
<spring-ai.version>2.0.0-SNAPSHOT</spring-ai.version>
<embabel.version>2.0.0-SNAPSHOT</embabel.version>
```

Both are resolved from non-Maven-Central repositories:

```xml
<repository>
  <id>spring-snapshots</id>
  <url>https://repo.spring.io/snapshot</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```

Spring Boot itself is also a SNAPSHOT:

```xml
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-parent</artifactId>
<version>4.1.0-SNAPSHOT</version>
```

### Problems

| Problem | Detail |
|---|---|
| **Non-reproducible builds** | SNAPSHOT artifacts are mutable. A `./mvnw package` today and tomorrow may resolve different bytecode for `spring-ai-bom:2.0.0-SNAPSHOT`. CI passes today; a breaking API change in the SNAPSHOT breaks CI without any commit in this repo. |
| **No offline builds** | CI requires network access to `repo.spring.io/snapshot` on every run. No `-o` (offline) mode possible. |
| **Embabel 2.0.0-SNAPSHOT** | Embabel is an experimental framework; API surface is unstable. `TuluatGoalAgent` and `CrdEmbabelConfiguration` depend on `ProviderInitialization`, `RegisteredModel`, `LlmService` — all from an unreleased API. |
| **Spring Boot 4.1.0-SNAPSHOT** | Spring Boot 4.x is not yet GA. Virtual threads, Jakarta EE 11, and Java 25 preview features are all in flux. The `--enable-preview` flag in CI (`-Denable-preview`) signals reliance on preview features that may change before GA. |
| **`okhttp 4.12.0` pinned via `minio-okhttp3.version`** | MinIO SDK pulls OkHttp 4.x while other transitive dependencies may expect 3.x or 5.x. This is currently managed, but a SNAPSHOT BOM update may silently resolve a different OkHttp version. |

### Current Mitigation

CI uses GHA layer cache for Docker and Maven `.m2` cache (`cache: 'maven'`), which reduces but does
not eliminate the SNAPSHOT churn risk — the cache expires on branch changes.

## Impact

- Any upstream SNAPSHOT release can break this project's build without a commit here.
- E2E tests on KinD rely on the same SNAPSHOT-built image; a midnight SNAPSHOT push can turn green CI red by morning.
- Not deployable to production as-is — most enterprise registries block SNAPSHOT dependencies.

## Proposed Remediation Path

1. **Pin Spring AI to a milestone** — use `2.0.0-M8` (or latest RC) instead of SNAPSHOT. Spring AI publishes milestone releases to Maven Central.
2. **Pin Spring Boot to the latest RC** — `4.1.0-RC1` resolves from `repo.spring.io/milestone`, not snapshot. Milestones are immutable.
3. **Embabel** — if no milestone exists, vendor the required Embabel API surface into `tuluat-engine` under an `internal.embabel` package until a stable release is available.
4. **Lock SNAPSHOT with a timestamp qualifier** — as an interim measure, fix the resolved SNAPSHOT artifact via `<version>2.0.0-20260811.120000-42</version>` in a local `.m2` cache committed to the repo (Maven local repo artifact lock pattern).

## Risk if not addressed

- CI green → red flip at any time with no code change.
- Cannot ship to a production Kubernetes cluster that enforces artifact integrity (OPA/Gatekeeper image provenance policies).
