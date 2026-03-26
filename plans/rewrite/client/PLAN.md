# Rewrite Client Plan

## Scope
- Own the rewrite Swing client only.
- Preserve exact visual parity for all retained client-facing UI.
- Use strict MVC for every post-login window family.

## Locked Client Rules
- Login UI is the only allowed code-copy exception.
- Post-login UI is entirely Kotlin rewrite code.
- Root controller owns service connections, state stores, selector registration, and top-level routing.
- Every post-login family uses `FrameHost` or dialog host + `View` + immutable `ViewModel` + `Controller`.
- Views only build Swing components and render supplied models.
- Views never talk to transport directly.
- Views do not know `RewriteRootController`, do not subscribe to selectors, do not launch coroutines, and do not register action listeners themselves.
- Controllers translate UI actions into commands and requests, derive models, bind listeners, and own disposal behavior.
- Exact legacy UI parity is mandatory; no rewrite-original layouts, styling, or chrome are allowed for retained surfaces.
- Rewrite-owned copies of legacy static UI assets are allowed when exact parity requires them; legacy runtime classes remain forbidden.
- Every computer identity is a real dotted-quad IPv4 address.
- `Command Prompt` and all Hacktendo surfaces are out of rewrite scope.

## Recovery Status Rules
- A task may stay `done` only when its title and notes clearly describe transport, decode, or bootstrap foundation work.
- Any retained post-login user-visible family remains `in_progress` until strict MVC, exact legacy screenshot parity, real IPv4 identity, and deterministic UI verification all pass.
- No client feature row in the feature inventory may be `done` until its foundation task and its parity-completion task are both complete.

## Core Foundation Tasks
### RW-CLIENT-001A - Root frame, root controller, and frame-store foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-M1-003`, `RW-PROTO-003`
- Allowed write scope: `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Owns root-controller and selector foundation only.
  - Does not claim post-login MVC or shell parity closure.

### RW-CLIENT-001B1 - Shared protocol shell contracts and decoded game selectors
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001A`, `RW-M1-003`, `RW-PROTO-003`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Shared protocol DTOs decode existing server JSON without adding `:RewriteGameCore` dependencies to the client.
  - This is transport and selector foundation only.

### RW-CLIENT-001B2 - Decoded chat contracts and merged shell notice selectors
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001B1`, `RW-CHAT-005`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient`, `:RewriteClientModel`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Decode chat-event payloads only after `RW-CHAT-005` freezes their parity surface.

### RW-CLIENT-002 - Login UI copy and rewrite auth/bootstrap flow
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001B1`, `RW-PROTO-001`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - This is the one accepted copied UI surface and is already parity-backed.
  - Bootstrap only the rewrite GAME service in this tranche; chat stays deferred.

### RW-CLIENT-003A - Desktop shell host, menu bar, and taskbar foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001B1`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Covers desktop-host, menu taxonomy, and single-instance wiring foundation only.
  - Exact shell chrome parity is deferred to `RW-CLIENT-S1`.

### RW-CLIENT-003B1 - Shell chrome data-binding foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Current shell stats/countdown binding landed as functional foundation only.
  - Reopened because the stats rail, CPU gauge, player identity, and shell chrome are not at strict MVC or exact legacy parity.

### RW-CLIENT-003B2 - Bottom message surface and popup or text notice routing
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003B1`, `RW-CLIENT-001B2`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Popup/dialog routing and bottom-shell messaging stay together in this tranche.

### RW-CLIENT-004 - Deterministic dev run foundation and parity harness bootstrap
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`, `RW-CLIENT-002`
- Allowed write scope: `:RewriteClientDev`, `:RewriteClient`, `:RewriteClientModel`, `:RewriteTestKit`, root Gradle/docs
- Verification command: `./gradlew :RewriteClientDev:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - The deterministic launcher exists, but the seeded world still needs real IPv4 identity, parity-ready fixtures, and screenshot-harness support.
  - Keep `:RewriteClient:run` unchanged as the live-edge path.

## Recovery Foundation Tasks
### RW-CLIENT-X1 - Strict MVC retrofit foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-001A`, `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Define the rewrite UI architecture contract for every post-login family.
  - Add shared base types for immutable view models, controller disposal, and host/view wiring.
  - Document package boundaries so Swing view classes stay dumb and controllers stay non-visual.

### RW-CLIENT-X2 - HackWars look-and-feel and parity asset foundation
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-X1`
- Allowed write scope: `:RewriteClient`, root docs
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Install a rewrite-owned port of the legacy Metal-based HackWars look and feel before any frame or dialog is created.
  - Create a rewrite-owned parity asset pack for legacy images, tiles, gauges, and renderer assets needed for exact UI recreation.

### RW-CLIENT-X3 - Canonical IPv4 identity cleanup
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-004`
- Allowed write scope: `:RewriteClientDev`, `:RewriteClient`, `:RewriteClientModel`, `:RewriteProtocol`, `:RewriteTestKit`, `:RewriteGameCore`, root docs
- Verification command: `./gradlew :RewriteClientDev:test :RewriteClientModel:test :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Replace `LOCAL-IP`, `TARGET-IP`, `ZOMBIE-IP`, `store1`, and every other non-IP placeholder in runtime code, test kit, dev mode, and tests.
  - Lock deterministic rewrite fixtures to RFC 5737 documentation ranges.

### RW-CLIENT-X4 - Screenshot parity harness
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-X2`, `RW-TEST-004`
- Allowed write scope: `:RewriteClient`, `:RewriteTestKit`, root docs
- Verification command: `./gradlew :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Capture deterministic legacy baselines for shell, stats, network, port management, and port scan first.
  - Add rewrite screenshot comparison gates and failure artifacts.

## Window Family Foundations
### RW-CLIENT-W1A - Banking windows functional foundation and correlated command broker
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/economy/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers correlated GAME command broker plus functional deposit, withdraw, and transfer flows.
  - Exact legacy layout and strict MVC closure move to `RW-CLIENT-S4`.

### RW-CLIENT-W1B - Bounty dialog and local file-picker functional foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W1A`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteClient/client/economy/**`, `:RewriteClient/client/files/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Functional bounty flow exists, but legacy dialog parity and strict MVC closure are still pending.

### RW-CLIENT-W2A - Filesystem decode, Home window, and local chooser foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClientModel`, `:RewriteClient/client/files/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers local filesystem decode and chooser foundation only.
  - `Home` remains a retained UI family and is not parity-complete yet.

### RW-CLIENT-W2B1 - Requestfile decode and file-view foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/files/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Functional file open, File Properties, and read-only Script Editor foundation landed.
  - Image-viewer parity stays blocked until rewrite exposes typed image-file metadata.

### RW-CLIENT-W2B2 - Script Editor mutation-flow foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2B1`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/files/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers save/new/save-as plus compile/decompile behavior as functional foundation.
  - Exact editor parity and strict MVC closure move to `RW-CLIENT-S4`.

### RW-CLIENT-W2C1 - Shop FTP seller surface and Public FTP read-only foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2A`, `RW-CLIENT-W5B2`
- Allowed write scope: `:RewriteClient/client/files/**`, `:RewriteClient/client/network/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Seller-side and read-only FTP behavior exists as functional foundation.
  - Exact shell FTP parity and strict MVC closure move to `RW-CLIENT-S4`.

### RW-CLIENT-W2C2 - FTP transfer parity and public-FTP password flows
- Status: `blocked`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2C1`
- Allowed write scope: `:RewriteClient/client/files/**`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Blocked until rewrite exposes equivalents for `put`, `get`, `malget`, and `setftppassword`.

### RW-CLIENT-W3A - Browser/store transport and lightweight HTML foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers `requestwebpage`, `submit`, `vote`, `exit`, `requestpurchase`, and functional rewrite browser/store behavior.
  - Exact legacy browser parity remains pending.

### RW-CLIENT-W3B - Site Editor request/save foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W3A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `Website Editor` transport and functional edit/save loop foundation.
  - Exact legacy surface and strict MVC closure move to `RW-CLIENT-S4`.

### RW-CLIENT-W3C - Help, tutorial, and browser-rendering parity hardening
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W3A`
- Allowed write scope: `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns retained `Help` and `Tutorial` surfaces plus browser-rendering parity work.

### RW-CLIENT-W4A - Port Management decoded-state and transport foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Reopened because the current `Port Management` surface is functional but not legacy-parity and not strict MVC.
  - Owns `healport`, `installapplication`, and `installfirewall` client transport decode/dispatch only.

### RW-CLIENT-W4B - Equipment and Firewall Manager functional foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W4A`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Functional inventory surfaces landed, but exact legacy recreation and strict MVC closure remain pending.

### RW-CLIENT-W4C - Watch Manager functional foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W4A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns watch decode/mutations and the functional rewrite Watch Manager foundation.
  - Exact legacy parity and strict MVC closure move to `RW-CLIENT-S6`.

### RW-CLIENT-W5A - Network-state decode plus Network and Port Scan functional foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClientModel`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Reopened because `Network` and `Port Scan` are currently smart frames with rewrite-original UI, not strict MVC or exact legacy parity.

### RW-CLIENT-W5B1 - Attack and Redirect pane functional foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5A`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Functional attack/redirect transport, chooser, and runtime loop landed.
  - Exact legacy pane parity and strict MVC closure move to `RW-CLIENT-S5`.

### RW-CLIENT-W5B2 - show_choices and remote follow-up foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5B1`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Shared remote-directory follow-up behavior exists as functional foundation only.

### RW-CLIENT-W5C - Zombie Attack launcher and pane foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Functional zombie launch/runtime behavior landed.
  - Exact legacy pane parity and strict MVC closure move to `RW-CLIENT-S5`.

### RW-CLIENT-W6 - Chat shell, relations, and messaging views
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`, `RW-CHAT-005`
- Allowed write scope: `:RewriteClient/client/chat/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Preserve channel, whisper, and relation semantics under the same MVC/parity rules.

### RW-CLIENT-W7A - Preferences, Log Window, and startup utility foundation
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteClient/client/utilities/**`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Functional `Preferences...`, `Log Window`, and startup preference behavior landed.
  - Exact legacy parity and strict MVC closure move to `RW-CLIENT-S6`.

### RW-CLIENT-W7B - Personal Settings and remaining utility follow-up
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W7A`
- Allowed write scope: `:RewriteClient/client/utilities/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `Personal Settings` and any remaining rewrite-backed non-chat utility follow-up once supporting profile data or mutation transport exists.

## Completion Tasks
### RW-CLIENT-S1 - Shell chrome parity completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`, `RW-CLIENT-003B1`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X3`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/shell/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Rebuild the desktop shell, taskbar, header chrome, and stats rail to match the legacy shell exactly.
  - Use `StatsPanel`, `StatIcon`, `MoneyIcon`, `BarPanel`, `LevelPanel`, and `CPULoadIcon` as the legacy reference set.

### RW-CLIENT-S2 - Network and Port Scan parity completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5A`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X3`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Rebuild `Network` from `MapPanel`, `NetworkPanel`, `NetworkInfoPanel`, and `NetworkMapPanel`.
  - Rebuild `Port Scan` from `PortScan`, `PortScanTableModel`, and `PortScanTableCellRenderer`.

### RW-CLIENT-S3 - Port Management parity completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W4A`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Replace the current table-driven rewrite UI with the legacy tabbed, per-port, icon-driven composition from `PortManagement` and `PortManagementMouseListener`.

### RW-CLIENT-S4 - Economy, files, editors, FTP, and browser-family parity completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W1A`, `RW-CLIENT-W1B`, `RW-CLIENT-W2A`, `RW-CLIENT-W2B1`, `RW-CLIENT-W2B2`, `RW-CLIENT-W2C1`, `RW-CLIENT-W3A`, `RW-CLIENT-W3B`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/economy/**`, `:RewriteClient/client/files/**`, `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Deposit, withdraw, transfer, Create Bounty, Home, chooser flows, Script Editor, File Properties, Shop FTP, Public FTP, Web Browser, Store, and Website Editor all get the same MVC retrofit and exact-legacy-layout pass.
  - Browser and editor work must use legacy window references from the feature inventory before coding resumes in those families.

### RW-CLIENT-S5 - Combat and network-runtime parity completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5B1`, `RW-CLIENT-W5B2`, `RW-CLIENT-W5C`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Attack, Redirect, Choices follow-ups, Zombie Attack, and related combat panes get the same MVC retrofit and exact legacy surface recreation.

### RW-CLIENT-S6 - Inventory, watch, and utility parity completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W4B`, `RW-CLIENT-W4C`, `RW-CLIENT-W7A`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/systems/**`, `:RewriteClient/client/utilities/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Equipment Manager, Firewall Manager, Watch Manager, Preferences, Log Window, and future Personal Settings all get the same MVC/parity treatment.

### RW-CLIENT-S7 - Chat, Help, and Tutorial completion
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W6`, `RW-CLIENT-W3C`, `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X4`
- Allowed write scope: `:RewriteClient/client/chat/**`, `:RewriteClient/client/web/**`, `:RewriteClient/client/utilities/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Keep chat as its own family lane.
  - Keep `Help` and `Tutorial` in the web/help family, not in utilities.

## Verification Gates
- Every family needs isolated controller tests.
- Every family needs screenshot parity tests against deterministic legacy baselines.
- Every family needs full integration UI tests before being marked `done`.
- No retained post-login UI task may be `done` until strict MVC, exact legacy parity, real IPv4 identity, and deterministic UI verification all pass.
