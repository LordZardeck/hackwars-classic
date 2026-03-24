# Rewrite Data Plan

## Scope
- Own the rewrite PostgreSQL schema, migrations, rollback guarantees, Docker runtime, and legacy importer.
- The rewrite schema is not compatible with legacy storage and does not try to be.

## Locked Defaults
- Database: PostgreSQL in Docker for local dev.
- Migration tool: Liquibase with explicit rollback support.
- Integration DB: Testcontainers PostgreSQL.
- Import direction: legacy MySQL/XML/JSON -> new PostgreSQL only.

## Core Tasks
### RW-DATA-001 - Stand up Dockerized PostgreSQL developer runtime
- Status: `done`
- Owner: `codex`
- Depends on: `RW-M1-001`
- Allowed write scope: `docker-compose.rewrite.yml`, `:RewritePersistence`
- Verification command: `docker compose -f docker-compose.rewrite.yml config`
- Artifacts: `docker-compose.rewrite.yml`
- Commit rule: `single green commit only`
- Notes:
  - Compose runtime exists and validates cleanly.
  - Runtime scripts and onboarding docs still need to be added.

### RW-DATA-002 - Create Liquibase changelog and rollback discipline
- Status: `done`
- Owner: `codex`
- Depends on: `RW-M1-001`
- Allowed write scope: `:RewritePersistence/src/main/resources/db/changelog/**`
- Verification command: `./gradlew :RewritePersistence:test`
- Artifacts: `src/RewritePersistence/src/main/resources/db/changelog`
- Commit rule: `single green commit only`
- Notes:
  - Bootstrap rewrite schema and explicit rollback are implemented.
  - Canonical schema breadth is still pending.

### RW-DATA-003 - Define canonical rewrite schema
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-DATA-002`
- Allowed write scope: `:RewritePersistence`
- Verification command: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Cover auth/session metadata, player state, NPC state, chat relations, events, snapshots, and audit tables.
  - Typed game-state persistence now covers event and snapshot storage for the M5 proof slice.
  - Broader NPC/chat/audit tables are still pending.

### RW-DATA-004 - Build legacy importer skeleton
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-DATA-003`
- Allowed write scope: `:RewritePersistence`
- Verification command: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Structural planner/sink abstractions exist for legacy MySQL/XML/JSON sources.
  - JDBC seed sink now writes import batches plus minimal account/computer/inventory records into the rewrite schema.
  - Importer smoke fixtures now seed root, `/Public`, `/Store`, migration-note files, and an installable banking binary so filesystem/install slices have realistic migrated bootstrap data.
  - Write only new PostgreSQL entities and snapshots.

### RW-DATA-005 - Add importer validation suite
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-DATA-004`
- Allowed write scope: `:RewritePersistence`, `:RewriteTestKit`
- Verification command: `./gradlew :RewritePersistence:test :RewriteMigrationTest`
- Artifacts: `build/reports/rewrite/migration`
- Commit rule: `single green commit only`
- Notes:
  - Smoke coverage now includes migration up/down, seed-batch planning, and seeded account/computer import writes.
  - Representative player, NPC, website, relation, and purchase imports still need broader validation.

## Verification Gates
- Every migration must have a tested rollback path.
- Importer tasks must emit actionable reports for mismapped legacy data.
