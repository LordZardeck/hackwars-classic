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
### RW-CLIENT-001 - Build root frame, root controller, and selector wiring
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-M1-003`, `RW-PROTO-003`
- Allowed write scope: `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteClient:test :RewriteClientModel:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Root controller owns both game and chat connections.
  - Stores must be normalized and selector-driven.

### RW-CLIENT-002 - Copy login UI and attach rewrite auth/bootstrap flow
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001`, `RW-PROTO-001`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - This is the only permitted copied code.
  - Preserve exact login visual and interaction behavior.

### RW-CLIENT-003 - Implement base desktop shell and taskbar
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Includes menu taxonomy, taskbar, countdown area, message surfaces, and shell-level stats bindings.

## Window Family Lanes
### RW-CLIENT-W1 - Banking and economy windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
- Allowed write scope: `:RewriteClient/client/economy/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Deposit, withdraw, transfer, and related dialogs.

### RW-CLIENT-W2 - Home, filesystem, FTP, and editor windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
- Allowed write scope: `:RewriteClient/client/files/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Home, file properties, image viewing, script editor, website editor, FTP.

### RW-CLIENT-W3 - Browser, store, help, tutorial windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
- Allowed write scope: `:RewriteClient/client/web/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Includes known legacy browser-rendering problem as required parity work.

### RW-CLIENT-W4 - Port, watch, equipment, and firewall windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
- Allowed write scope: `:RewriteClient/client/systems/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Selector updates must be fine-grained by watched value, not whole-state blasts.

### RW-CLIENT-W5 - Network, scan, attack, redirect, zombie windows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
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
- Depends on: `RW-CLIENT-003`, `RW-CHAT-005`
- Allowed write scope: `:RewriteClient/client/chat/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Preserve channel, whisper, and relation semantics.

### RW-CLIENT-W7 - Settings, logs, command prompt, misc utilities
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
- Allowed write scope: `:RewriteClient/client/utilities/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Includes personal settings, preferences, help, log window, and command prompt.

### RW-CLIENT-W8 - Hacktendo creator and player
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003`
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
