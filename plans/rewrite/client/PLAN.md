# Rewrite Client Plan

## Scope
- Own the rewrite Swing client only.
- Preserve exact visual parity for the game client UI.
- Use MVC for all post-login window families.

## Locked Client Rules
- Login UI is the only allowed code-copy exception.
- Post-login UI is entirely Kotlin rewrite code.
- Root controller owns service connections, state stores, selector registration, and top-level routing.
- Views never talk to transport directly.
- Controllers translate UI actions into commands and requests.
- Views subscribe only through fine-grained selectors.

## Core Tasks
### RW-CLIENT-001A - Build root frame, root controller, and opaque frame-store foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-M1-003`, `RW-PROTO-003`
- Allowed write scope: `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Root controller owns both game and chat connections.
  - Stores must be normalized and selector-driven.
  - Snapshot, delta, program-update, game-ui, and chat payloads stay opaque in this tranche.

### RW-CLIENT-001B1 - Add shared protocol shell contracts and decoded game selectors
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001A`, `RW-M1-003`, `RW-PROTO-003`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Shared protocol DTOs must decode the existing server JSON payloads without adding a `:RewriteGameCore` dependency to the client.
  - Keep chat opaque in this tranche; decode only shell-facing game snapshot, delta, program-update, and game-ui payloads.

### RW-CLIENT-001B2 - Add decoded chat contracts and merged shell notice selectors
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001B1`, `RW-CHAT-005`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Decode chat-event payloads only after `RW-CHAT-005` freezes their protocol parity surface.
  - Merge game and chat notice selectors only once both event families are typed.

### RW-CLIENT-002 - Copy login UI and attach rewrite auth/bootstrap flow
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001B1`, `RW-PROTO-001`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - This is the only permitted copied code.
  - Preserve exact login visual and interaction behavior.
  - Bootstrap only the rewrite GAME service in this tranche; chat stays deferred.

### RW-CLIENT-003A - Implement desktop shell host, menu bar, taskbar, and internal-window foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001B1`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Covers the rewrite-owned desktop host, legacy menu taxonomy, embedded taskbar, and single-instance placeholder windows.
  - Keeps countdown, stats, and message surfaces deferred.

### RW-CLIENT-003B1 - Implement shell stats rail, player-title binding, and countdown chrome
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Binds decoded runtime and shell snapshot data into the desktop chrome without adding client-model or protocol changes.
  - Keeps bottom message routing and popup or text notice handling deferred to `RW-CLIENT-003B2`.

### RW-CLIENT-003B2 - Implement bottom message surface plus popup or text notice routing
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003B1`, `RW-CLIENT-001B2`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Merges decoded game/chat notices into the shell message surfaces only after `RW-CLIENT-001B2` lands.
  - Popup/dialog routing and bottom-shell messaging stay together in this tranche.

## Window Family Lanes
### RW-CLIENT-W1A - Deposit, withdraw, and transfer windows plus correlated economy command responses
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/economy/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers the client-side correlated GAME command broker plus rewrite-owned deposit, withdraw, and transfer windows.
  - Keeps `Create Bounty` deferred until filesystem-backed file/path picking is available.

### RW-CLIENT-W1B - Create Bounty dialog and file-picker follow-up
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W1A`, `RW-CLIENT-W2`
- Allowed write scope: `:RewriteClient/client/economy/**`, `:RewriteClient/client/files/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Depends on `RW-CLIENT-W2` because bounty creation needs filesystem-backed file and path selection.

### RW-CLIENT-W2 - Home, filesystem, FTP, and editor windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/files/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Home, file properties, image viewing, script editor, website editor, FTP.

### RW-CLIENT-W3 - Browser, store, help, tutorial windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/web/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Includes known legacy browser-rendering problem as required parity work.

### RW-CLIENT-W4 - Port, watch, equipment, and firewall windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/systems/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Selector updates must be fine-grained by watched value, not whole-state blasts.

### RW-CLIENT-W5 - Network, scan, attack, redirect, zombie windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/network/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Scan uses one-shot response flow.
  - Attack windows bind to long-running program updates plus deltas.

### RW-CLIENT-W6 - Chat shell, relations, and messaging views
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`, `RW-CHAT-005`
- Allowed write scope: `:RewriteClient/client/chat/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Preserve channel, whisper, and relation semantics.

### RW-CLIENT-W7 - Settings, logs, command prompt, misc utilities
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/utilities/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Includes personal settings, preferences, help, log window, and command prompt.

### RW-CLIENT-W8 - Hacktendo creator and player
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/hacktendo/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Includes currently broken-but-required player launch parity.

## Verification Gates
- Every family needs isolated controller tests.
- Every family needs screenshot parity tests.
- Every family needs full integration UI tests before being marked `done`.
