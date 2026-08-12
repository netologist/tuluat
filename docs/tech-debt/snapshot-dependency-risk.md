# Tech Debt: SNAPSHOT / Pre-Release Dependency Risk [RESOLVED]

- **Status:** Resolved (2026-08-12)
- **Commit:** `ad1648a`
- **Resolution:** Spring Boot 4.1.0 and Spring AI 2.0.0 pinned to GA releases on Maven Central. `spring-snapshots` repository removed. Embabel 2.0.0-SNAPSHOT remains (no 2.x GA release as of Aug 2026).

## Resolution Summary

| Change | Detail |
|---|---|
| **Spring Boot parent** | `4.1.0-SNAPSHOT` → `4.1.0` GA (released June 10, 2026) |
| **Spring AI BOM** | `2.0.0-SNAPSHOT` → `2.0.0` GA (released June 12, 2026) |
| **Repository cleanup** | `spring-snapshots` repo + pluginRepo removed; `spring-milestones` kept for future RCs |
| **Embabel** | Kept at `2.0.0-SNAPSHOT` — only remaining SNAPSHOT; `embabel-snapshots` repo retained |

## Original Analysis

- **Date:** 2026-08-12
- **Severity:** High
- **Module:** Root `pom.xml`

### Problems (pre-resolution)

| Problem | Detail |
|---|---|
| **Non-reproducible builds** | SNAPSHOT artifacts mutable; CI could break without any commit. |
| **No offline builds** | CI required network access to `repo.spring.io/snapshot` every run. |
| **Embabel 2.0.0-SNAPSHOT** | Unstable API surface; `TuluatGoalAgent` and `CrdEmbabelConfiguration` depend on unreleased API. |
| **Spring Boot 4.1.0-SNAPSHOT** | Pre-GA; Java 25 preview features in flux. |
| **`okhttp` version pinning** | `minio-okhttp3.version=4.12.0` could be silently overridden by BOM update. |

### Risk if not addressed

- CI green → red flip at any time with no code change.
- Cannot ship to production Kubernetes clusters with artifact integrity policies.
