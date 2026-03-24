# Rewrite Game Server Plan

## Scope
- Own the rewrite game-server runtime and all server-side game behavior.
- Never import legacy runtime code.
- Preserve gameplay behavior from legacy evidence, not legacy architecture.

## Architecture Checklist
### RW-GS-001 - Define canonical server contracts
- Status: `done`
- Owner: `codex`
- Depends on: `RW-M1-003`
- Allowed write scope: `:RewriteGameCore`
- Verification command: `./gradlew :RewriteGameCore:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Stabilize `GameStateStore`, `InterestRegistry`, `CommandContext`, `DeltaPublisher`, and `ProgramScheduler`.
  - Freeze the minimum public interfaces before slice work fans out.
  - Typed `ComputerState`, `ComputerEvent`, `ComputerDelta`, `ProgramUpdate`, and command contracts are now the canonical server surface.

### RW-GS-002 - Implement event-first state persistence hooks
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-001`, `RW-DATA-002`
- Allowed write scope: `:RewriteGameCore`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Every mutation appends an event immediately.
  - Snapshot save policy defaults to `50 events or 5 seconds`.
  - JDBC repository coverage now proves typed event append, replay, JSON-byte roundtrip, and threshold-triggered snapshots.

### RW-GS-003 - Implement interest registry fanout
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-001`
- Allowed write scope: `:RewriteGameCore`
- Verification command: `./gradlew :RewriteGameCore:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - One connection may subscribe to multiple game states.
  - All interested connections receive deltas for changed states.
  - Reverse cleanup now removes leaked subscriptions when a connection is torn down.

### RW-GS-004 - Implement request and callback command path
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-001`, `RW-PROTO-002`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Support fire-and-forget and request commands.
  - Request commands return through callbacks and correlation IDs.
  - `requestscan` and `setpreferences` now prove decode -> dispatch -> correlated response behavior through the rewrite harness.

### RW-GS-005 - Implement isolated program scheduler
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-001`
- Allowed write scope: `:RewriteGameCore`
- Verification command: `./gradlew :RewriteGameCore:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Attack-like programs run in isolated coroutines with explicit lifetime and cancellation.
  - Programs publish scoped status updates plus resulting deltas.
  - Virtual-time tests now cover start, tick, completion, explicit cancellation, and lifetime expiry.

## Vertical Slice Board
### RW-GS-S1 - Session bootstrap, auth success path, initial snapshot, ping, reconnect
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-GS-001`, `RW-PROTO-001`
- Allowed write scope: `:RewriteGameServer`, `:RewriteGameCore`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Send exactly one full snapshot after login.
  - No deltas before session bootstrap completes.
  - Proof slice landed for auth success plus one bootstrap snapshot.
  - Reconnect and full persistence-backed profile loading still need follow-on slice work.

### RW-GS-S2 - Filesystem, files, scripts, FTP, equipment install, firewall install
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S1`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Cover directory requests, file load/save, compile/decompile, and install flows.
  - Separate one-shot responses from state deltas.
  - Typed filesystem state now uses `currentPath`, `directoriesByPath`, and `filesByPath` with canonical slash-prefixed paths.
  - Rewrite game-core tests now cover request-directory/request-file/request-secondary-directory, path normalization, save/create/delete/delete-multi, compile/decompile petty-cash and XP side effects, banking-app install, equipment install, and firewall replacement semantics.
  - Rewrite game-server harness tests now prove one-shot read responses, targeted filesystem/economy/port deltas, banking default-port selection, and replaced-firewall return-to-disk behavior.

### RW-GS-S3 - Economy, websites, store, banking, purchases, resale, votes
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-S1`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Cover deposit, withdraw, transfer, votes, purchases, and website save/load.

### RW-GS-S4 - Network switching, scan, quests, tasks, clues, search, bounties
- Status: `in_progress`
- Owner: `codex`
- Depends on: `RW-GS-S1`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Scan must be a single correlated response, not an ongoing subscription.
  - Proof slice landed for `requestscan`; the rest of the feature family is still pending.

### RW-GS-S5 - Combat, redirect, zombie attack, watches, long-running programs
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-005`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Programs publish periodic attack updates.
  - Resulting state changes fan out to all interested subscribers.

### RW-GS-S6 - Hacktendo-specific server behavior
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-S2`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Preserve required Hacktendo flows, including currently broken legacy ones.

## Verification Gates
- Every command gets mocked tests for accepted input, rejected input, lifetime expiry, emitted deltas, nested dispatch, and cancellation.
- Every slice must add integration tests before its task can be marked `done`.
- No slice is `done` until linked rows in `plans/rewrite/feature-inventory/PLAN.md` are updated.
