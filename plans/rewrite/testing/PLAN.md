# Rewrite Testing Plan

## Scope
- Own the rewrite verification model across unit, component, integration, migration, UI, screenshot, architecture-guardrail, and artifact-capture layers.

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

### RW-TEST-002 - Artifact capture conventions
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-TEST-001`
- Allowed write scope: `:RewriteBuildLogic`, `:RewriteTestKit`
- Verification command: `./gradlew rewriteCheck`
- Artifacts: `build/reports/rewrite`
- Commit rule: `single green commit only`
- Notes:
  - Integration and UI failures must emit logs, screenshots, and protocol traces.
  - Screenshot diff failures must emit the baseline, actual, and diff images for the failing surface.

### RW-TEST-003 - Testcontainers-backed rewrite integration harness
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-DATA-001`
- Allowed write scope: `:RewriteTestKit`, `:RewritePersistence`
- Verification command: `./gradlew rewriteIntegrationTest rewriteMigrationTest`
- Artifacts: `build/reports/rewrite/integration`
- Commit rule: `single green commit only`
- Notes:
  - Rewrite persistence has Testcontainers-backed migration and repository coverage.
  - Full-stack multi-service rewrite integration still needs isolated JVM-process coverage beyond the in-memory harness.

### RW-TEST-004 - Deterministic UI harness and screenshot baseline system
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-X2`
- Allowed write scope: `:RewriteClient`, `:RewriteTestKit`
- Verification command: `./gradlew rewriteUiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Fix look and feel, font configuration, DPI, window size, and theme so visual comparisons are deterministic.
  - Capture deterministic legacy baselines for shell, stats, network, port management, and port scan first.
  - No retained client-facing family may be marked `done` before a screenshot parity baseline exists for that family.

### RW-TEST-005 - MVC architecture guardrail tests
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-X1`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Fail when a view class imports `RewriteRootController`, protocol DTOs, client stores, or coroutine APIs.
  - Fail when a view constructor or initialization path subscribes to a selector, launches a coroutine, or registers listeners directly.
  - Fail when a controller directly subclasses Swing containers instead of owning a host and view.

### RW-TEST-006 - Legacy parity capture workflow
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-TEST-004`
- Allowed write scope: `:RewriteClient`, `:RewriteTestKit`, root docs
- Verification command: `./gradlew :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Define how legacy reference screenshots are captured, named, reviewed, and refreshed.
  - Require every retained client-facing feature row to link to at least one legacy baseline and one rewrite parity screenshot.

## Acceptance Gates
- No milestone is `done` without linked passing automated tests.
- No retained post-login client family is `done` without controller tests, UI workflow tests, screenshot parity tests, and MVC guardrail coverage.
- Feature rows in the feature inventory must name their legacy UI reference and parity acceptance view before implementation resumes in that family.
