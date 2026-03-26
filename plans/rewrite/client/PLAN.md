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
- Status: `done`
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
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W1A`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteClient/client/economy/**`, `:RewriteClient/client/files/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Depends on `RW-CLIENT-W2A` because bounty creation needs local filesystem-backed file and path selection, not the whole editor/FTP lane.

### RW-CLIENT-W2A - Filesystem decode, Home window, and reusable local chooser foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClientModel`, `:RewriteClient/client/files/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers local filesystem decode, the rewrite `Home` window, and the reusable local open-file chooser foundation.
  - Visible directory contents come from correlated `requestdirectory` responses, with client-owned displayed-path state and refresh-on-filesystem-delta behavior.

### RW-CLIENT-W2B1 - Requestfile decode, Home file actions, File Properties, and read-only Script Editor foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/files/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers `requestfile`, Home file-open actions, rewrite `File Properties`, and the first read-only `Script Editor` host.
  - Routes `SCRIPT_SOURCE`, `TEXT`, and `NOTE` into the editor and all other currently typed file kinds into `File Properties`.
  - Image-viewer parity stays explicitly deferred until rewrite exposes typed image-file metadata; no filename/content heuristics are allowed in this tranche.

### RW-CLIENT-W2B2 - Script Editor save/new/save-as plus compile/decompile mutation flows
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2B1`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/files/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Adds editable Script Editor flows on top of the `RW-CLIENT-W2B1` read-only foundation.
  - Covers `savefile`, `compilefile`, `decompilefile`, and dirty-state handling without taking ownership of `Website Editor`.
  - Image-viewer parity stays deferred until rewrite exposes typed image-file metadata; no filename/content heuristics are allowed in this tranche.

### RW-CLIENT-W2C1 - Shell-launched Shop FTP seller surface and Public FTP read-only browser
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2A`, `RW-CLIENT-W5B2`
- Allowed write scope: `:RewriteClient/client/files/**`, `:RewriteClient/client/network/**`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers shell-launched seller-side `Shop FTP`, read-only `Public FTP`, `sellfile`, and reuse of the shared `requestsecondarydirectory` remote browser.
  - Store buying remains owned by `RW-CLIENT-W3A`; this slice is seller-side shell FTP and read-only public browsing only.

### RW-CLIENT-W2C2 - FTP transfer parity and public-FTP password flows
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W2C1`
- Allowed write scope: `:RewriteClient/client/files/**`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Extends the `RW-CLIENT-W2C1` FTP windows with legacy transfer parity once rewrite transport exists.
  - Blocked until rewrite exposes equivalents for `put`, `get`, `malget`, and `setftppassword`.

### RW-CLIENT-W3A - Lightweight browser core, store purchase flow, and web transport bridge
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers `requestwebpage`, `submit`, `vote`, `exit`, `requestpurchase`, and real rewrite `Web Browser` / `Store` windows.
  - Uses a rewrite-owned lightweight Swing HTML surface in this first browser tranche; no legacy Lobo/`HtmlHandler` port is allowed here.
  - Store is purchase-only in this slice; seller/merchant management remains deferred.

### RW-CLIENT-W3B - Site Editor with `requestpage` / `savepage`
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W3A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `Website Editor` because it depends on `requestpage` / `savepage`, not filesystem commands.
  - Reuses the browser/web transport bridge from `RW-CLIENT-W3A`.

### RW-CLIENT-W3C - Help, tutorial, and browser-rendering parity hardening
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W3A`
- Allowed write scope: `:RewriteClient/client/web/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `Help` and `Tutorial` windows plus broader browser-rendering parity work.
  - Search/bookmarks remain out of scope because the legacy remote endpoints behind them are gone.

### RW-CLIENT-W4A - Port Management core on decoded port state plus heal/install transport
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Covers the first real rewrite `Port Management` window over decoded port snapshot/delta state.
  - Owns `healport`, `installapplication`, and `installfirewall` client transport decode/dispatch only.
  - Shows enabled/default/dummy/note fields read-only in this slice; `portonoff`, default/dummy toggles, note editing, and public FTP password mutation stay deferred.

### RW-CLIENT-W4B - Equipment Manager and Firewall Browser inventory surfaces
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W4A`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns the real rewrite `Equipment Manager` and `Firewall Manager` windows over decoded inventory state.
  - Adds only the missing client `installequipment` decode/dispatch and reuses chooser/install flows from `RW-CLIENT-W4A`.
  - Equipment repair, equipment removal, and firewall removal remain deferred until rewrite transport exists.

### RW-CLIENT-W4C - Watch Manager plus watch protocol decode and mutations
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W4A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/systems/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `fetchwatches` plus watch mutation decode/dispatch and the real rewrite `Watch Manager`.
  - Selector updates must be fine-grained by watched value, not whole-state blasts.

### RW-CLIENT-W5A - Network window, network-state decode, and port scan foundation
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-003A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClientModel`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns decoded rewrite `network` state, `changenetwork`, and one-shot `requestscan`.
  - Replaces the `Network` and `Port Scan` placeholders with real rewrite-owned windows.

### RW-CLIENT-W5B1 - Attack Port and Redirect Port panes plus local attack-file chooser
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5A`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `requestattack`, `requestcancelattack`, real rewrite `Attack Port` / `Redirect Port` panes, local attack loadout serialization, and window-handle correlation for pane messages.
  - Keeps `show_choices` and remote follow-up browsing out of scope so the first attack tranche stays focused on the real pane/runtime loop.

### RW-CLIENT-W5B2 - show_choices, requestsecondarydirectory, and shared remote follow-up browser
- Status: `done`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5B1`, `RW-CLIENT-W2A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns decoded `show_choices`, `requestsecondarydirectory`, and the shared remote-target chooser/browser follow-up.
  - `RW-CLIENT-W2C1` depends on this slice because shell FTP and attack-family follow-up share the same secondary-directory targeting surface.

### RW-CLIENT-W5C - Zombie Attack launcher and pane
- Status: `in_progress`
- Owner: `unassigned`
- Depends on: `RW-CLIENT-W5A`
- Allowed write scope: `:RewriteProtocol`, `:RewriteClient/client/network/**`, `:RewriteClient`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`
- Artifacts: `build/reports/rewrite/ui`
- Commit rule: `single green commit only`
- Notes:
  - Owns `requestzombieattack` and `requestzombiecancelattack` plus the rewrite zombie-attack launcher/pane.
  - Keeps the longer-running zombie program lifecycle isolated from the initial decode-and-discovery slice in `RW-CLIENT-W5A`.
  - Defers zombie-triggered `show_choices` follow-up handling because the current server emits those notices with `windowHandle = 0`.

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
