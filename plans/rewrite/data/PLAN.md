# Rewrite Data Plan

## Scope
- This plan covers retained rewrite persistence, schema, importer, and validation work that backs the retained game server, retained chat server, and retained client roadmap.
- Data work must be split into disjoint domain slices so it can run in parallel with retained server, chat, and client work.

## Locked Defaults
- PostgreSQL remains the canonical retained rewrite datastore.
- Liquibase remains the retained schema migration tool.
- Importer work must stay deterministic and auditable; silent best-effort migration is not acceptable.
- Data tasks must expose exact upstream schema IDs that unblock retained server or chat work.

## Execution Lanes
| Lane | Scope | Primary write scope |
| --- | --- | --- |
| `runtime` | local Postgres runtime and test harness setup | `docker-compose.rewrite.yml`, `src/RewritePersistence/**`, `src/RewriteTestKit/**` |
| `schema` | retained schema slices for auth, player, world, and chat | `src/RewritePersistence/**` |
| `importer` | importer adapters and migration runners | `src/RewritePersistence/**`, `src/RewriteMigrationTest/**` |
| `audit` | validation fixtures, reconciliation, and migration evidence | `src/RewritePersistence/**`, `src/RewriteMigrationTest/**`, `plans/rewrite/feature-inventory/PLAN.md` |

## Task Cards
### RW-DATA-001 - Stand up Dockerized PostgreSQL developer runtime
- Status: `done`
- Priority: `P0`
- Execution lane: `runtime`
- Worker role: `worker`
- Depends on: `RW-M1-001`
- Ready when: rewrite developer runtime can be containerized
- Parallel with: `RW-DATA-002`
- Allowed write scope: `docker-compose.rewrite.yml`, `src/RewritePersistence/**`
- Autonomous next: `RW-DATA-002`
- Fallback if blocked: `none`
- Verification scope: `docker compose -f docker-compose.rewrite.yml config`

### RW-DATA-002 - Create Liquibase changelog and rollback discipline
- Status: `done`
- Priority: `P0`
- Execution lane: `schema`
- Worker role: `worker`
- Depends on: `RW-DATA-001`
- Ready when: retained persistence module exists
- Parallel with: `RW-TEST-003`
- Allowed write scope: `src/RewritePersistence/**`
- Autonomous next: `RW-DATA-003A`
- Fallback if blocked: `RW-TEST-003`
- Verification scope: `./gradlew :RewritePersistence:test`

### RW-DATA-003A - Define retained auth and session schema slice
- Status: `done`
- Priority: `P1`
- Execution lane: `schema`
- Worker role: `worker`
- Depends on: `RW-DATA-002`
- Ready when: Liquibase foundation is stable
- Parallel with: `RW-DATA-003B`, `RW-DATA-003D`
- Allowed write scope: `src/RewritePersistence/**`
- Autonomous next: `RW-DATA-004A`
- Fallback if blocked: `RW-DATA-003B`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`

### RW-DATA-003B - Define retained player and computer schema slice
- Status: `done`
- Priority: `P1`
- Execution lane: `schema`
- Worker role: `worker`
- Depends on: `RW-DATA-002`
- Ready when: Liquibase foundation is stable
- Parallel with: `RW-DATA-003A`, `RW-DATA-003C`
- Allowed write scope: `src/RewritePersistence/**`
- Autonomous next: `RW-DATA-004A`
- Fallback if blocked: `RW-DATA-003A`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`

### RW-DATA-003C - Define retained world, network, NPC, and website schema slice
- Status: `done`
- Priority: `P1`
- Execution lane: `schema`
- Worker role: `worker`
- Depends on: `RW-DATA-002`
- Ready when: Liquibase foundation is stable
- Parallel with: `RW-DATA-003B`, `RW-DATA-003D`
- Allowed write scope: `src/RewritePersistence/**`
- Autonomous next: `RW-DATA-004B`
- Fallback if blocked: `RW-DATA-003B`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`

### RW-DATA-003D - Define retained chat and social schema slice
- Status: `done`
- Priority: `P1`
- Execution lane: `schema`
- Worker role: `worker`
- Depends on: `RW-DATA-002`
- Ready when: Liquibase foundation is stable
- Parallel with: `RW-DATA-003A`, `RW-DATA-003C`
- Allowed write scope: `src/RewritePersistence/**`
- Autonomous next: `RW-DATA-004B`
- Fallback if blocked: `RW-DATA-003A`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`

### RW-DATA-004A - Build importer slices for retained auth, player, and computer data
- Status: `done`
- Priority: `P2`
- Execution lane: `importer`
- Worker role: `worker`
- Depends on: `RW-DATA-003A`, `RW-DATA-003B`
- Ready when: retained auth/session and player/computer schema slices are complete
- Parallel with: `RW-DATA-004B`
- Allowed write scope: `src/RewritePersistence/**`, `src/RewriteMigrationTest/**`
- Autonomous next: `RW-DATA-005A`
- Fallback if blocked: `RW-DATA-003B`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`

### RW-DATA-004B - Build importer slices for retained world, website, chat, and social data
- Status: `done`
- Priority: `P2`
- Execution lane: `importer`
- Worker role: `worker`
- Depends on: `RW-DATA-003C`, `RW-DATA-003D`
- Ready when: retained world/network/NPC and chat/social schema slices are complete
- Parallel with: `RW-DATA-004A`
- Allowed write scope: `src/RewritePersistence/**`, `src/RewriteMigrationTest/**`
- Autonomous next: `RW-DATA-005A`
- Fallback if blocked: `RW-DATA-003C`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest`

### RW-DATA-005A - Add retained importer validation fixtures and migration evidence
- Status: `ready`
- Priority: `P2`
- Execution lane: `audit`
- Worker role: `worker`
- Depends on: `RW-DATA-004A`, `RW-DATA-004B`
- Ready when: retained importer slices exist across auth, player, world, and chat domains
- Parallel with: `RW-TEST-003`
- Allowed write scope: `src/RewriteMigrationTest/**`, `src/RewritePersistence/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-DATA-005B`
- Fallback if blocked: `RW-DATA-004A`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest :RewriteMigrationTest:test`

### RW-DATA-005B - Add retained audit and reconciliation reporting
- Status: `todo`
- Priority: `P3`
- Execution lane: `audit`
- Worker role: `verifier`
- Depends on: `RW-DATA-005A`
- Ready when: importer validation fixtures and migration evidence exist
- Parallel with: `RW-M2-001`
- Allowed write scope: `src/RewritePersistence/**`, `src/RewriteMigrationTest/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-M2-001`
- Fallback if blocked: `RW-DATA-005A`
- Verification scope: `./gradlew :RewritePersistence:test :RewritePersistence:migrationTest :RewriteMigrationTest:test`

## Global Acceptance Gates
- No retained data task is `done` until migration tests, persistence tests, and any named downstream unblock verification remain green.
- `RW-GS-T4` depends on `RW-DATA-003B`.
- `RW-CHAT-002A`, `RW-CHAT-003A`, and `RW-CHAT-004A` depend on `RW-DATA-003A` or `RW-DATA-003D` exactly as named above.
