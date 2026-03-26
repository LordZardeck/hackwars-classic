# Rewrite Game Server Plan

## Scope
- This plan covers retained rewrite GAME-server work that still backs the retained client roadmap.
- Hacktendo server work is removed from rewrite scope.
- Client blockers may only point at exact task IDs in this file; vague placeholder blockers are not allowed.

## Locked Server Rules
- Keep only retained server work that still backs the retained client, retained chat stack, or retained persistence roadmap.
- Search and browser lookup support already landed as retained foundation and should not be reopened unless a retained downstream task names an exact parity gap.
- New retained transport tasks must name the exact downstream client tasks they unblock.
- Server tasks that only exist to support removed client scope must be marked `removed` instead of staying `todo`.

## Execution Lanes
| Lane | Scope | Primary write scope |
| --- | --- | --- |
| `core_runtime` | contracts, callbacks, scheduler, persistence hooks, interest fanout | `src/RewriteGameCore/**`, `src/RewriteGameServer/**` |
| `retained_transports` | retained GAME commands and callback payloads that unblock client work | `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**` |
| `combat_runtime` | attack, redirect, zombie, show-choices follow-up, runtime finalizers | `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteHackScript/**` |
| `profile_help` | retained personal settings, help, tutorial, and public FTP password flows | `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**` |

## Foundation Tasks
### RW-GS-001 - Canonical server contracts foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `core_runtime`
- Worker role: `worker`
- Depends on: `RW-M1-003`
- Ready when: rewrite game-core contracts can be stabilized
- Parallel with: `RW-GS-003`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-GS-004`
- Fallback if blocked: `none`
- Verification scope: `./gradlew :RewriteGameCore:test`

### RW-GS-002 - Event-first persistence hook foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `core_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-001`, `RW-DATA-002`
- Ready when: persistence adapters and event sinks can be exercised
- Parallel with: `RW-GS-003`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-GS-F1`
- Fallback if blocked: `RW-GS-003`
- Verification scope: `./gradlew :RewriteGameCore:test :RewritePersistence:test`

### RW-GS-003 - Interest registry and fanout foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `core_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-001`
- Ready when: session fanout and subscription seams exist
- Parallel with: `RW-GS-002`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`
- Autonomous next: `RW-GS-004`
- Fallback if blocked: `RW-GS-F1`
- Verification scope: `./gradlew :RewriteGameCore:test`

### RW-GS-004 - Request and callback command path foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `retained_transports`
- Worker role: `worker`
- Depends on: `RW-GS-001`, `RW-PROTO-002`
- Ready when: correlated request and callback handling can be validated
- Parallel with: `RW-GS-005`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-GS-F1`
- Fallback if blocked: `RW-GS-005`
- Verification scope: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`

### RW-GS-005 - Isolated program scheduler foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `core_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-001`
- Ready when: retained program runtime can be scheduled deterministically
- Parallel with: `RW-GS-004`
- Allowed write scope: `src/RewriteGameCore/**`
- Autonomous next: `RW-GS-F5`
- Fallback if blocked: `RW-GS-F1`
- Verification scope: `./gradlew :RewriteGameCore:test`

### RW-GS-F1 - Session bootstrap, auth success path, initial snapshot, and reconnect foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `core_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-002`, `RW-GS-003`, `RW-GS-004`
- Ready when: retained bootstrap flow can complete end to end
- Parallel with: `RW-GS-F2`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-GS-F2`
- Fallback if blocked: `RW-GS-F3`
- Verification scope: `./gradlew :RewriteGameServer:test`

### RW-GS-F2 - Filesystem, economy, website, browser, and store foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `retained_transports`
- Worker role: `worker`
- Depends on: `RW-GS-F1`
- Ready when: retained filesystem and economy command set is stable
- Parallel with: `RW-GS-F3`, `RW-GS-T1`, `RW-GS-T3`, `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-GS-F3`
- Fallback if blocked: `RW-GS-T1`
- Verification scope: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-F3 - Network, scan, search, world lookup, and watch foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `retained_transports`
- Worker role: `worker`
- Depends on: `RW-GS-F1`
- Ready when: retained network and search flows are stable
- Parallel with: `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-GS-F4`
- Fallback if blocked: `RW-GS-T5`
- Verification scope: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-F4 - Watch manager and trigger engine foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `core_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-F2`, `RW-GS-F3`, `RW-GS-005`
- Ready when: retained watch flows and trigger execution are stable
- Parallel with: `RW-GS-F5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteHackScript/**`
- Autonomous next: `RW-GS-F5`
- Fallback if blocked: `RW-GS-T6`
- Verification scope: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewriteGameServer:test`

### RW-GS-F5 - Attack, redirect, and zombie runtime foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-005`, `RW-GS-F4`
- Ready when: retained combat runtime and callback correlation are stable enough to finish missing protocol gaps
- Parallel with: `RW-GS-T1`, `RW-GS-T4`, `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteHackScript/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-GS-T6`
- Fallback if blocked: `RW-GS-T1`
- Verification scope: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

## Retained Client-Unblocking Tasks
### RW-GS-T1 - Implement retained FTP upload and download transport
- Status: `done`
- Priority: `P1`
- Execution lane: `retained_transports`
- Worker role: `worker`
- Depends on: `RW-GS-F2`
- Ready when: retained filesystem foundation is complete
- Parallel with: `RW-GS-T3`, `RW-GS-T4`, `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-GS-T2`
- Fallback if blocked: `RW-GS-T3`
- Verification scope: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-T2 - Implement retained `malget` and theft-transfer transport
- Status: `ready`
- Priority: `P1`
- Execution lane: `retained_transports`
- Worker role: `worker`
- Depends on: `RW-GS-T1`, `RW-GS-F5`
- Ready when: retained FTP upload or download transport and retained combat filesystem hooks are complete
- Parallel with: `RW-GS-T4`, `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CLIENT-W2C2`
- Fallback if blocked: `RW-GS-T1`
- Verification scope: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-T3 - Implement retained public FTP password transport
- Status: `done`
- Priority: `P1`
- Execution lane: `profile_help`
- Worker role: `worker`
- Depends on: `RW-GS-F2`
- Ready when: retained FTP foundation and persistence hooks are stable
- Parallel with: `RW-GS-T1`, `RW-GS-T4`, `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CLIENT-W2C2`
- Fallback if blocked: `RW-GS-T1`
- Verification scope: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-T4 - Implement retained personal-settings and profile-mutation transport
- Status: `ready`
- Priority: `P2`
- Execution lane: `profile_help`
- Worker role: `worker`
- Depends on: `RW-GS-F1`, `RW-DATA-003B`
- Ready when: retained session bootstrap and player or computer schema slices are stable
- Parallel with: `RW-GS-T1`, `RW-GS-T3`, `RW-GS-T5`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CLIENT-W7B`
- Fallback if blocked: `RW-GS-T5`
- Verification scope: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test`

### RW-GS-T5 - Implement retained Help and Tutorial content or query transport
- Status: `ready`
- Priority: `P2`
- Execution lane: `profile_help`
- Worker role: `worker`
- Depends on: `RW-GS-F2`, `RW-GS-F3`
- Ready when: retained browser, website, and search foundations are complete
- Parallel with: `RW-GS-T1`, `RW-GS-T3`, `RW-GS-T4`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteProtocol/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CLIENT-W3C2`
- Fallback if blocked: `RW-GS-T4`
- Verification scope: `./gradlew :RewriteGameCore:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-T6 - Close retained attack and redirect runtime finalizers and protocol parity
- Status: `ready`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-F5`
- Ready when: retained combat runtime foundation is sufficiently stable to close remaining protocol gaps
- Parallel with: `RW-GS-T1`, `RW-GS-T4`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteHackScript/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-GS-T7`
- Fallback if blocked: `RW-GS-T1`
- Verification scope: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

### RW-GS-T7 - Close retained zombie and show-choices follow-up parity transport
- Status: `todo`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-GS-T6`
- Ready when: retained attack or redirect protocol parity is closed and zombie follow-up correlation gaps can be addressed
- Parallel with: `RW-GS-T2`
- Allowed write scope: `src/RewriteGameCore/**`, `src/RewriteGameServer/**`, `src/RewriteHackScript/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C9B`
- Fallback if blocked: `RW-GS-T6`
- Verification scope: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewriteGameServer:test :RewriteTestKit:integrationTest`

## Client Unblock Map
| Client task | Upstream server task(s) |
| --- | --- |
| `RW-CLIENT-W2C2` | `RW-GS-T2` |
| `RW-CLIENT-W3C2` | `RW-GS-T5` |
| `RW-CLIENT-W7B` | `RW-GS-T4` |
| `RW-CLIENT-C9B` | `RW-GS-T6`, `RW-GS-T7` |

## Global Acceptance Gates
- No retained server task is `done` until protocol tests, core tests, persistence tests where applicable, and retained integration tests are green.
- A retained client blocker may only name tasks from the `Retained Client-Unblocking Tasks` section or `blocked_external`.
