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
  - `triggerWatch` and `triggerWatchRemote` now record typed `WatchTriggerIntent` objects through `HookSideEffectSink`; explicit execution binding now lives in `RW-GS-S5A2` while passive and combat-oriented watch behavior remains deferred.
  - Request/submit flush order is now fixed to deltas, then UI events, then correlated response. Exit remains fire-and-forget with side effects only.

### RW-GS-S3B3B - Bind HTTP watch-trigger intents into the real watch engine
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S3B3A`, `RW-GS-S5A2`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Consumes `WatchTriggerIntent` from the seam introduced in `RW-GS-S3B3A` and binds it into synchronous installed-watch execution during `requestwebpage`, `submit`, and `exit`.
  - Preserves the NPC-only gate on `triggerWatchRemote`, the non-fatal hook failure rules from `RW-GS-S3B2` and `RW-GS-S3B3A`, and the existing correlated browser response ordering.

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

### RW-GS-S4A - Quest progress, save files, clue compatibility, bounty creation, and trigger seams
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S1`, `RW-GS-S2`, `RW-GS-S3A`, `RW-GS-S3B3A`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Covers `requesttask`, `requestsave`, `cluedata`, `makebounty`, `requesttrigger`, and internal `requesttriggernote` compatibility.
  - Quest state now tracks active quest progress, completed quest ids, and passive clue payload compatibility state.
  - Save files now persist typed scalar metadata plus locked legacy text serialization, and bounty creation now writes typed bounty files into canonical shard-store inventory while debiting creator petty cash.
  - Explicit trigger requests now route into the shared typed `WatchTriggerIntentSink` seam without executing the watch system yet.

### RW-GS-S4B1 - Current-network state, switching, and typed scan parity
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S1`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Adds authoritative typed `NetworkState` on the player state, including the resolved shard-store state id, cooldown timestamp, allowed-network set, and current-network NPC directory lists.
  - Covers public `changenetwork`, internal-only `changenetwork2` compatibility via `ChangeNetworkDirectCommand`, and typed `requestscan` parity with requester petty-cash and scanning-XP side effects.
  - `requestscan` remains a single correlated response, now uses typed masked port/firewall views, and publishes requester `economy` plus `stats` deltas before the response.
  - `requestgame` is intentionally excluded from this slice; the rewrite network view is derived from session snapshot plus targeted `network` deltas instead of a one-shot fetch command.

### RW-GS-S4B2 - Rewrite-owned world directory and network access refresh
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S4B1`, `RW-M4-001`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Replaces the default in-memory world stub with rewrite-owned world-directory tables, JDBC repository coverage, and importer/seed payloads.
  - Adds internal-only `GrantNetworkAccessCommand` and `RefreshCurrentNetworkDirectoryCommand` so future quest/combat flows can unlock networks and refresh current-network directory state without adding new public transport commands.
  - Game bootstrap now refreshes current-network store routing and NPC lists before returning the one required snapshot, while still emitting no pre-bootstrap delta frames.
  - `changenetwork` continues to behave the same on the wire, but it now resolves switch validation text and destination directory data from rewrite-owned persistence rather than hardcoded defaults.

### RW-GS-S4C - Search and world/browser lookup flows
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-GS-S4B2`
- Allowed write scope: `:RewriteGameCore`, `:RewritePersistence`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns rewrite ranked website search on the existing game connection and keeps results request/response-only with no discovery subscriptions.
  - Preserves the legacy visibility gate: searchable sites must have active HTTP and belong to NPCs or players inactive for at least 14 days.
  - Keeps browser lookup narrow in this slice: `requestsearch` returns normalized site addresses for later `requestwebpage`, while remote domain lookup and broader world discovery remain deferred.

### RW-GS-S5A1 - Watch manager core
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-005`, `RW-GS-S4A`, `RW-GS-S2`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Covers `fetchwatches`, `installwatch`, `setwatchnote`, `setwatchonoff`, `setwatchquantity`, `setwatchobservedports`, `setwatchsearchfirewall`, `changewatchport`, `changewatchtype`, and `deletewatch`.
  - Adds typed `WatchManagerState`, `InstalledWatch`, legacy-capacity rules, and CPU-load bookkeeping without executing installed watch programs yet.
  - Public watch commands now persist first, publish typed `watches` and `runtime` deltas where required, and return correlated typed list/mutation responses on the rewrite game socket.
  - `requesttrigger` and HTTP hook `triggerWatch*` remain seam-only in this slice and continue emitting `WatchTriggerIntent` without binding into execution.

### RW-GS-S5A2 - Explicit watch-trigger execution and binding
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S5A1`, `RW-GS-S3B3A`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Binds existing `WatchTriggerIntent` from explicit trigger commands and HTTP hook seams into real installed-watch execution rules that run synchronously in the current command flow.
  - Keeps the supported helper surface intentionally narrow in this slice: explicit triggers can append host logs and deposit petty cash, while unsupported helpers fail that watch execution non-fatally.
  - Preserves the manager/config guarantees from `RW-GS-S5A1` and keeps missing or disabled matches as successful no-ops.

### RW-GS-S5A3A - Passive petty-cash and scan watches with fractional XP
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-GS-S5A2`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Adds single-pass passive petty-cash and scan auto-fire on top of the installed-watch execution path and keeps evaluation synchronous inside the originating command flow.
  - Migrates rewrite XP storage and response surfaces to fractional `Double` values, including watch XP awarded once per passive trigger pass.
  - Preserves the locked legacy petty-cash threshold and overheat gates, and keeps health-watch auto-fire deferred to `RW-GS-S5A3B`.

### RW-GS-S5A3B - Passive health-watch auto-fire
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S5A3A`, `RW-GS-S5B2A`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns passive `WatchKind.HEALTH` auto-fire on combat-driven port-health loss using the same installed-watch runtime and single-pass evaluation model as the earlier watch slices.
  - Health watchers now execute only from typed combat damage, refresh baselines on non-overheated matching ports, and keep heal or recovery-driven baseline resets deferred to a later runtime or heal slice.
  - Must not regress the explicit and passive petty-cash or scan behavior from earlier watch slices while `RW-GS-S5B2B2` remains deferred for richer combat helpers and firewall behavior.

### RW-GS-S5B1 - Attack start/cancel program foundation
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-005`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewritePersistence:migrationTest :RewriteGameServer:test :RewriteTestKit:integrationTest rewriteCheck`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns public `requestattack` and `requestcancelattack`, internal `requestattackdefault`, and scheduler-backed typed attack sessions on the attacker state only.
  - Preserves the locked start fee, CPU reservation, and source-port attacking bookkeeping while deferring target mutation, damage, rewards, and attack-script side effects.
  - Keeps `ProgramUpdate` as the only in-flight stream surface, with attacker-state deltas flushing before correlated start/cancel responses.

### RW-GS-S5B2A - Deterministic target-side combat damage and resolution
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S5B1`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Adds bilateral combat admission and cleanup, deterministic target-port damage ticks, attacker attack-XP rewards, and target incoming-lock persistence without introducing attack-script runtime yet.
  - Running ticks now mutate target port health, refresh attacker-side typed target views, and publish target deltas while keeping `ProgramUpdate` scoped to attacker-state listeners only.
  - Emits typed passive health-change triggers on combat damage so `RW-GS-S5A3B` can bind health-watch auto-fire without reworking the combat loop.

### RW-GS-S5B2B1 - Installed attack-script runtime core
- Status: `done`
- Owner: `codex`
- Depends on: `RW-GS-S5B2A`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Runs installed attack-application `INITIALIZE`, `CONTINUE`, and `FINALIZE` script slots from `ProgramScriptBundle` using the rewrite-native HackScript runtime.
  - Keeps the effect surface intentionally narrow in this tranche: attack scripts can read typed combat context and append attacker-side host logs only.
  - Preserves the deterministic damage and attack-XP loop from `RW-GS-S5B2A`; parse/runtime failures are non-fatal and do not cancel the outer attack flow.

### RW-GS-S5B2B2 - Richer combat helpers and firewall behavior
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-S5B2B1`
- Allowed write scope: `:RewriteHackScript`, `:RewriteGameCore`, `:RewriteGameServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteHackScript:test :RewriteGameCore:test :RewritePersistence:test :RewriteGameServer:test :RewriteTestKit:integrationTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns richer firewall combat behavior, broader attack-side helpers, and any follow-on target-side combat semantics beyond the deterministic base loop plus installed attack-script runtime delivered in `RW-GS-S5B2A` and `RW-GS-S5B2B1`.
  - Keeps `launchnetworkattack` deferred until the rewrite has a typed redirecting or shipping-port model.

### RW-GS-S5C - Redirect, zombie, and combat cleanup
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-GS-S5B2B2`
- Allowed write scope: `:RewriteGameCore`, `:RewriteGameServer`
- Verification command: `./gradlew :RewriteGameCore:test :RewriteGameServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns redirect, zombie-attack, heal-port, finalize-cancelled, and the remaining combat cleanup flows once the base attack-program runtime exists.

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
