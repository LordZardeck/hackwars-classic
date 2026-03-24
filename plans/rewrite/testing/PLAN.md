# Rewrite Testing Plan

## Scope
- Own the rewrite verification model across unit, component, integration, migration, UI, screenshot, and artifact-capture layers.

## Rewrite Task Targets
- `rewriteUnitTest`
- `rewriteIntegrationTest`
- `rewriteUiTest`
- `rewriteMigrationTest`
- `rewriteCheck`
- `rewriteAll`

## Core Tasks
### RW-TEST-001 - Standardize rewrite test stack
- Status: `done`
- Owner: `codex`
- Depends on: `RW-M1-001`
- Allowed write scope: `:RewriteBuildLogic`, `src/Rewrite*/build.gradle`
- Verification command: `./gradlew rewriteUnitTest`
- Artifacts: `build/reports/tests`
- Commit rule: `single green commit only`
- Notes:
  - JUnit 5, MockK, and `kotlinx-coroutines-test` are wired into rewrite modules.
  - Dedicated integration, UI, and migration source-set/task conventions now run real source sets instead of placeholder lifecycle tasks.

### RW-TEST-002 - Add rewrite artifact-capture conventions
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-TEST-001`
- Allowed write scope: `:RewriteBuildLogic`, `:RewriteTestKit`
- Verification command: `./gradlew rewriteCheck`
- Artifacts: `build/reports/rewrite`
- Commit rule: `single green commit only`
- Notes:
  - Integration and UI failures must emit logs, screenshots, and protocol traces.

### RW-TEST-003 - Add Testcontainers-based integration harness
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-DATA-001`
- Allowed write scope: `:RewriteTestKit`, `:RewritePersistence`
- Verification command: `./gradlew rewriteIntegrationTest rewriteMigrationTest`
- Artifacts: `build/reports/rewrite/integration`
- Commit rule: `single green commit only`
- Notes:
  - Rewrite persistence now has Testcontainers-backed test and migration smoke coverage.
  - Typed game-core and rewrite game-server proof-slice tests now run green against the in-memory harness and JDBC repository.
  - Full-stack multi-service rewrite integration still needs isolated JVM-process coverage beyond the in-memory harness.

### RW-TEST-004 - Add UI screenshot baseline system
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
- Allowed write scope: `:RewriteClient`, `:RewriteTestKit`
- Verification command: `./gradlew rewriteUiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Fix window size, font configuration, DPI, and theme to make visual comparisons deterministic.

## Acceptance Gates
- No milestone is `done` without linked passing automated tests.
- Feature rows in the feature inventory must name their required tests before implementation starts.
