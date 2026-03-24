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

### RW-GS-S3A - Economy, banking, purchases, and resale
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S1`, `RW-GS-S2`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Covers deposit, withdraw, transfer, Facebook banking aliases, single-file pricing, multi-file liquidation, and request-purchase.
  - Store inventory remains inside typed filesystem state, with canonical shard-store routing through `store$serverId`.
  - Rewrite tests now prove canonical and Facebook banking wires, shard-store delta fanout, explicit revenue-target credits, and typed JDBC replay for economy/store events.

### RW-GS-S3B1 - Static website editor, browser core, fallback pages, and vote
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S3A`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Covers `requestpage`, `savepage`, `requestwebpage`, `submit`, `exit`, and `vote`.
  - Browser/editor page loads remain correlated request responses; no rewrite `webpage` push command was introduced.
  - Static page rendering now returns the canonical legacy fallback page when the target lacks an active default HTTP site.
  - Vote handling is normalized into a clean hard-failure path and now updates voter website votes, target website vote count, and target HTTP/Webdesign XP in one typed transaction.

### RW-GS-S3B2 - Programmable HTTP enter/submit/exit hook runtime
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S3B1`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Adds executable HTTP `enter`, `submit`, and `exit` behavior on top of the `HttpHookRuntime` seam introduced in `RW-GS-S3B1`.
  - Static page serving, fallback handling, and vote behavior are already covered and must not regress.
  - The rewrite now preserves three-slot HTTP script bundles across save/request/compile/decompile/install, persists them through snapshots/events/import seeds, and executes them through the Kotlin-only `:RewriteHackScript` module.
  - `submit` now runs `submit` then `enter` against a shared mutable body/include-store state, while parse/runtime failures fall back to the static `RW-GS-S3B1` render path.
  - Daily pay, empty-petty-cash, and social/Facebook stubs remain outside this slice.

### RW-GS-S3B3A - HTTP hook side-effect seams
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S3B2`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewriteProtocol`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Implements `logMessage`, `popUp`, and `triggerWatch`/`triggerWatchRemote` as rewrite-safe side-effect seams on top of the phased HTTP hook runtime.
  - `logMessage` now appends persisted bounded host log state and emits `logs` deltas to host listeners.
  - `popUp` now emits typed transient `GameUiEventEnvelope` frames to the requesting connection only and preserves the legacy four-popup cap per hook execution slot.
  - `triggerWatch` and `triggerWatchRemote` now record typed `WatchTriggerIntent` objects through `HookSideEffectSink`; actual watch gameplay remains deferred.
  - Request/submit flush order is now fixed to deltas, then UI events, then correlated response. Exit remains fire-and-forget with side effects only.

### RW-GS-S3B3B - Bind HTTP watch-trigger intents into the real watch engine
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-S3B3A`, `RW-GS-S5`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Consumes `WatchTriggerIntent` from the seam introduced in `RW-GS-S3B3A` and binds it into the rewrite watch manager and watch execution rules.
  - Must preserve the NPC-only gate on `triggerWatchRemote` and the non-fatal hook failure rules from `RW-GS-S3B2` and `RW-GS-S3B3A`.

### RW-GS-S3B3C - Broader HackScript side-effect helpers and client/system-message parity
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-S3B3A`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewriteProtocol`, `:RewriteClient`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewriteGameServer:test :RewriteClient:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns any remaining side-effectful linker helpers beyond the `RW-GS-S3B3A` seam, including richer system-message and popup parity work once the rewrite client utility/message slices exist.
  - May broaden HackScript compatibility only after preserving the render-path and side-effect ordering guarantees already covered by `RW-GS-S3B2` and `RW-GS-S3B3A`.

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
