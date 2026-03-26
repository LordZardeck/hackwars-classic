# Rewrite Client Plan

## Scope
- This plan covers the retained rewrite client: login, desktop shell, economy, files, FTP, browser and website flows, systems management, network and combat panes, utilities, help and tutorial, client chat, and deterministic dev-mode investigation.
- Hacktendo and Command Prompt are removed from rewrite client scope.
- `Set Public FTP Password` and FTP transfer parity remain in retained scope, but they may only advance when concrete retained game-server transport tasks are available.

## Locked Client Rules
- Every retained post-login surface must use `FrameHost` or dialog host plus `View` plus immutable `ViewModel` plus `Controller`.
- Views only build Swing components and render a supplied model.
- Views do not import `RewriteRootController`, client stores, protocol contracts, or coroutine APIs.
- Views do not subscribe to selectors and do not register action listeners in constructors or initialization blocks.
- Controllers own selector subscriptions, listener binding, model derivation, child-window launches, request routing, and disposal.
- Exact legacy UI parity is mandatory; rewrite-original layouts or styling are not acceptable end states.
- The rewrite-owned HackWars cross-platform look and feel must be installed before any retained frame or dialog is created.
- Every computer identity shown or used by the rewrite client must be a real dotted-quad IPv4 address.
- A retained client family cannot be marked `done` until its controller tests, UI workflow tests, screenshot parity tests, MVC guardrails, and deterministic dev-mode verification are green.

## Autonomous Scheduling Rules
- The coordinator should consume this plan by pass order: cross-cutting foundations, shell recovery, network and systems, economy and files, combat runtime, utilities and chat.
- Foundation cards may remain `done` only when they are explicitly narrowed to transport, decoding, or non-parity scaffolding.
- Every parity or user-visible completion task must remain `todo`, `ready`, `in_progress`, `blocked`, or `blocked_external` until screenshot-backed parity closure lands.
- When a task becomes `blocked`, the coordinator should immediately schedule the task named in `Fallback if blocked` if it is `ready`.

## Execution Lanes
| Lane | Scope | Primary write scope |
| --- | --- | --- |
| `auth_bootstrap` | login, startup bootstrap, deterministic dev harness | `src/RewriteClient/**/auth/**`, `src/RewriteClientDev/**` |
| `shell_chrome` | desktop shell, stats rail, taskbar, popup and bottom-message routing | `src/RewriteClient/**/shell/**`, `src/RewriteClient/**/desktop/**`, `src/RewriteClient/**/utilities/**` |
| `economy_files` | banking, bounty, home, chooser, file view, editor, image and binary viewers | `src/RewriteClient/**/files/**`, `src/RewriteClient/**/economy/**` |
| `ftp_web` | shop FTP, public FTP, browser, store, site editor, help, tutorial | `src/RewriteClient/**/network/**`, `src/RewriteClient/**/web/**` |
| `systems_network` | network map, port scan, port management, equipment, firewall, watch | `src/RewriteClient/**/network/**`, `src/RewriteClient/**/systems/**` |
| `combat_runtime` | attack, redirect, show-choices follow-up, zombie attack | `src/RewriteClient/**/network/**`, `src/RewriteClient/**/combat/**` |
| `utilities_chat` | preferences, log window, personal settings, client chat | `src/RewriteClient/**/utilities/**`, `src/RewriteClient/**/chat/**` |
| `cross_cutting` | MVC base types, look and feel, parity assets, IPv4 cleanup, screenshot harness glue | `src/RewriteClient/**`, `src/RewriteClientDev/**`, `src/RewriteClientModel/**`, `src/RewriteProtocol/**` |

## Foundation Tasks
### RW-CLIENT-001A - Root frame, root controller, and frame-store foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `cross_cutting`
- Worker role: `worker`
- Depends on: `RW-M1-003`, `RW-PROTO-003`
- Ready when: retained rewrite client bootstrap exists
- Parallel with: `RW-CLIENT-001B1`
- Allowed write scope: `src/RewriteClient/**`, `src/RewriteClientModel/**`
- Autonomous next: `RW-CLIENT-X1`
- Fallback if blocked: `none`
- Verification scope: `./gradlew :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-001B1 - Shared protocol shell contracts and decoded game selectors foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `cross_cutting`
- Worker role: `worker`
- Depends on: `RW-CLIENT-001A`, `RW-M1-003`, `RW-PROTO-003`
- Ready when: protocol decode and selector seams can be validated
- Parallel with: `RW-CLIENT-002`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteClientModel/**`
- Autonomous next: `RW-CLIENT-001B2`
- Fallback if blocked: `RW-CLIENT-002`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test`

### RW-CLIENT-001B2 - Decoded chat contracts and merged shell notice selectors
- Status: `blocked`
- Priority: `P1`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-001B1`, `RW-CHAT-005A`
- Ready when: chat protocol parity events are available from `RW-CHAT-005A`
- Parallel with: `RW-CLIENT-003B2`, `RW-CLIENT-W6`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteClientModel/**`, `src/RewriteClient/**/shell/**`, `src/RewriteClient/**/chat/**`
- Autonomous next: `RW-CLIENT-W6`
- Fallback if blocked: `RW-CLIENT-003B2`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test`

### RW-CLIENT-002 - Login UI copy and rewrite auth/bootstrap foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `auth_bootstrap`
- Worker role: `worker`
- Depends on: `RW-CLIENT-001B1`, `RW-PROTO-001`
- Ready when: auth bootstrap seam exists
- Parallel with: `RW-CLIENT-004A`
- Allowed write scope: `src/RewriteClient/**/auth/**`, `src/RewriteClientDev/**`
- Autonomous next: `RW-CLIENT-C0A`
- Fallback if blocked: `RW-CLIENT-004A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-003A - Desktop shell host, menu bar, and taskbar foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `shell_chrome`
- Worker role: `worker`
- Depends on: `RW-CLIENT-001B1`
- Ready when: desktop shell host exists
- Parallel with: `RW-CLIENT-003B1`
- Allowed write scope: `src/RewriteClient/**/shell/**`, `src/RewriteClient/**/desktop/**`
- Autonomous next: `RW-CLIENT-C1A`
- Fallback if blocked: `RW-CLIENT-X1`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-003B1 - Shell state selectors and data-binding foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `shell_chrome`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: shell state can be surfaced without parity guarantees
- Parallel with: `RW-CLIENT-004A`
- Allowed write scope: `src/RewriteClient/**/shell/**`, `src/RewriteClientModel/**`
- Autonomous next: `RW-CLIENT-C1A`
- Fallback if blocked: `RW-CLIENT-X1`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-003B2 - Bottom message surface and popup or text notice routing foundation
- Status: `blocked`
- Priority: `P1`
- Execution lane: `shell_chrome`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003B1`, `RW-CLIENT-001B2`
- Ready when: merged shell notice selectors from `RW-CLIENT-001B2` exist
- Parallel with: `RW-CLIENT-C1A`, `RW-CLIENT-W6`
- Allowed write scope: `src/RewriteClient/**/shell/**`, `src/RewriteClient/**/desktop/**`
- Autonomous next: `RW-CLIENT-C1B`
- Fallback if blocked: `RW-CLIENT-C1A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-004A - Deterministic dev run foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `auth_bootstrap`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`, `RW-CLIENT-002`
- Ready when: deterministic dev launcher can open the real rewrite desktop
- Parallel with: `RW-CLIENT-X1`
- Allowed write scope: `src/RewriteClientDev/**`, `src/RewriteTestKit/**`
- Autonomous next: `RW-CLIENT-004B`
- Fallback if blocked: `RW-CLIENT-X3`
- Verification scope: `./gradlew :RewriteClientDev:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-004B - Deterministic dev parity harness and usable-state closure
- Status: `done`
- Priority: `P1`
- Execution lane: `auth_bootstrap`
- Worker role: `worker`
- Depends on: `RW-CLIENT-004A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: look and feel and IPv4 cleanup are available to deterministic fixtures
- Parallel with: `RW-CLIENT-C1A`, `RW-TEST-004`
- Allowed write scope: `src/RewriteClientDev/**`, `src/RewriteTestKit/**`, `src/RewriteClient/**`
- Autonomous next: `RW-CLIENT-C1A`
- Fallback if blocked: `RW-CLIENT-X3`
- Verification scope: `./gradlew :RewriteClientDev:test :RewriteClient:uiTest`

### RW-CLIENT-X1 - Strict MVC retrofit foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `cross_cutting`
- Worker role: `worker`
- Depends on: `RW-CLIENT-001A`, `RW-CLIENT-003A`
- Ready when: shared client architecture can be updated without waiting on server transport
- Parallel with: `RW-TEST-005`, `RW-M2-001`
- Allowed write scope: `src/RewriteClient/**`, `src/RewriteClientModel/**`, `plans/rewrite/client/PLAN.md`
- Autonomous next: `RW-CLIENT-X2`
- Fallback if blocked: `RW-CLIENT-X3`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-X2 - HackWars look-and-feel and parity asset foundation
- Status: `done`
- Priority: `P0`
- Execution lane: `cross_cutting`
- Worker role: `worker`
- Depends on: `RW-CLIENT-X1`
- Ready when: MVC base types and host wiring rules are in place
- Parallel with: `RW-CLIENT-X3`, `RW-TEST-006`
- Allowed write scope: `src/RewriteClient/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-X4`
- Fallback if blocked: `RW-CLIENT-X3`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-X3 - Canonical IPv4 identity cleanup
- Status: `done`
- Priority: `P0`
- Execution lane: `cross_cutting`
- Worker role: `worker`
- Depends on: `RW-CLIENT-004A`
- Ready when: deterministic dev foundation and retained fixtures are available
- Parallel with: `RW-CLIENT-X2`, `RW-CLIENT-004B`
- Allowed write scope: `src/RewriteClient/**`, `src/RewriteClientDev/**`, `src/RewriteTestKit/**`, `src/RewriteClientModel/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-004B`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: `./gradlew :RewriteClientDev:test :RewriteClientModel:test :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-X4 - Screenshot parity harness glue for retained client families
- Status: `done`
- Priority: `P0`
- Execution lane: `cross_cutting`
- Worker role: `worker`
- Depends on: `RW-CLIENT-X2`, `RW-TEST-004`
- Ready when: rewrite look and feel is stable and deterministic screenshot infrastructure exists
- Parallel with: `RW-TEST-006`
- Allowed write scope: `src/RewriteClient/**`, `src/RewriteClient:uiTest/**`, `src/RewriteClientDev/**`
- Autonomous next: `RW-CLIENT-C0C`
- Fallback if blocked: `RW-TEST-004`
- Verification scope: `./gradlew :RewriteClient:uiTest`

### RW-CLIENT-W1A - Banking windows and correlated command broker foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: banking request and dialog scaffolding exists
- Parallel with: `RW-CLIENT-W1B`
- Allowed write scope: `src/RewriteClient/**/economy/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C2A`
- Fallback if blocked: `RW-CLIENT-C2A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W1B - Bounty dialog and local file-picker foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W1A`, `RW-CLIENT-W2A`
- Ready when: local chooser foundation exists
- Parallel with: `RW-CLIENT-W1A`, `RW-CLIENT-W2B1`
- Allowed write scope: `src/RewriteClient/**/economy/**`, `src/RewriteClient/**/files/**`
- Autonomous next: `RW-CLIENT-C2A`
- Fallback if blocked: `RW-CLIENT-C3A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W2A - Filesystem decode, Home window, and local chooser foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: filesystem decode and chooser seams exist
- Parallel with: `RW-CLIENT-W2B1`, `RW-CLIENT-W2C1`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteClientModel/**`, `src/RewriteClient/**/files/**`
- Autonomous next: `RW-CLIENT-C3A`
- Fallback if blocked: `RW-CLIENT-W2B1`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W2B1 - Requestfile decode and file-view foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2A`
- Ready when: requestfile transport and file rendering seam exist
- Parallel with: `RW-CLIENT-W2B2`
- Allowed write scope: `src/RewriteClient/**/files/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-W2B3`
- Fallback if blocked: `RW-CLIENT-C3A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W2B2 - Script Editor mutation-flow foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2B1`
- Ready when: save and mutation transport can be exercised
- Parallel with: `RW-CLIENT-W2B3`
- Allowed write scope: `src/RewriteClient/**/files/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C3A`
- Fallback if blocked: `RW-CLIENT-W2B3`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W2B3 - Binary and image file viewer foundation
- Status: `ready`
- Priority: `P2`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2B1`
- Ready when: requestfile foundation exists and retained file-type handling can be extended
- Parallel with: `RW-CLIENT-C3A`
- Allowed write scope: `src/RewriteClient/**/files/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C3A`
- Fallback if blocked: `RW-CLIENT-C3A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W2C1 - Shop FTP seller surface and Public FTP read-only foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2A`, `RW-CLIENT-W5B2`
- Ready when: local chooser and secondary directory foundation exist
- Parallel with: `RW-CLIENT-W3A`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/**/files/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C4A`
- Fallback if blocked: `RW-CLIENT-W2C2`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W2C2 - FTP transfer parity and public-FTP password flows
- Status: `done`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2C1`, `RW-GS-T2`
- Ready when: shell-launched FTP windows exist and retained `malget` transport exists from the retained game server
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/**/files/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C4B`
- Fallback if blocked: `RW-CLIENT-C4A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W3A - Browser, store, and lightweight HTML transport foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: requestwebpage, purchase, and HTML rendering foundation exist
- Parallel with: `RW-CLIENT-W3B`
- Allowed write scope: `src/RewriteClient/**/web/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-W3C1`
- Fallback if blocked: `RW-CLIENT-C5A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W3B - Site Editor request/save foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W3A`
- Ready when: requestpage and savepage transport are wired
- Parallel with: `RW-CLIENT-W3C1`
- Allowed write scope: `src/RewriteClient/**/web/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C5A`
- Fallback if blocked: `RW-CLIENT-W3C1`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W3C1 - Browser rendering parity hardening foundation
- Status: `ready`
- Priority: `P2`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W3A`
- Ready when: browser/store/site editor foundation exists and rendering differences can be audited
- Parallel with: `RW-CLIENT-C5A`
- Allowed write scope: `src/RewriteClient/**/web/**`
- Autonomous next: `RW-CLIENT-C5A`
- Fallback if blocked: `RW-CLIENT-C5A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W3C2 - Help and Tutorial surfaces foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W3A`, `RW-GS-T5`
- Ready when: retained help and tutorial server transport/content tasks are available from `RW-GS-T5`
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/web/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C5B`
- Fallback if blocked: `RW-CLIENT-C5A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W4A - Port Management decoded-state and transport foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: decoded port data and core mutations are wired
- Parallel with: `RW-CLIENT-W4B`, `RW-CLIENT-W4C`
- Allowed write scope: `src/RewriteClient/**/systems/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C7A`
- Fallback if blocked: `RW-CLIENT-C8A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W4B - Equipment and Firewall Manager foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W4A`, `RW-CLIENT-W2A`
- Ready when: decoded slot and port state can be rendered
- Parallel with: `RW-CLIENT-W4C`
- Allowed write scope: `src/RewriteClient/**/systems/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C8A`
- Fallback if blocked: `RW-CLIENT-C7A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W4C - Watch Manager foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W4A`
- Ready when: decoded watch state and watch mutations are wired
- Parallel with: `RW-CLIENT-W4B`
- Allowed write scope: `src/RewriteClient/**/systems/**`, `src/RewriteClientModel/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C8A`
- Fallback if blocked: `RW-CLIENT-C7A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W5A - Network-state decode plus Network and Port Scan foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: network decode, changenetwork, and requestscan foundations are wired
- Parallel with: `RW-CLIENT-W4A`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClientModel/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C6A`
- Fallback if blocked: `RW-CLIENT-C7A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W5B1 - Attack and Redirect pane foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W5A`, `RW-CLIENT-W2A`
- Ready when: attack request/cancel transport and chooser foundations exist
- Parallel with: `RW-CLIENT-W5C`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-W5B2`
- Fallback if blocked: `RW-CLIENT-C9A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W5B2 - show_choices and remote follow-up foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W5B1`, `RW-CLIENT-W2A`
- Ready when: show_choices, secondary directory, and follow-up browser foundation exist
- Parallel with: `RW-CLIENT-W2C1`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C9A`
- Fallback if blocked: `RW-CLIENT-C4A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W5C - Zombie Attack launcher and pane foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W5A`
- Ready when: zombie attack launcher and runtime foundation exist
- Parallel with: `RW-CLIENT-W5B2`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C9A`
- Fallback if blocked: `RW-CLIENT-C9A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W6 - Client chat shell, relations, and messaging foundation
- Status: `blocked`
- Priority: `P1`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-001B2`, `RW-CHAT-003B`, `RW-CHAT-004B`, `RW-CHAT-005A`
- Ready when: merged chat selectors, channel/message fanout, relations notifications, and parity events are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/chat/**`, `src/RewriteClientModel/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C11A`
- Fallback if blocked: `RW-CLIENT-C10A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W7A - Preferences, Log Window, and startup utility foundation
- Status: `done`
- Priority: `P1`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`
- Ready when: preference persistence, log rendering, and startup auto-open foundation exist
- Parallel with: `RW-CLIENT-W7B`
- Allowed write scope: `src/RewriteClient/**/utilities/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C10A`
- Fallback if blocked: `RW-CLIENT-C10A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-W7B - Personal Settings retained follow-up
- Status: `ready`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W7A`, `RW-GS-T4`
- Ready when: retained personal-settings/profile mutation transport exists from `RW-GS-T4`
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/utilities/**`, `src/RewriteProtocol/**`
- Autonomous next: `RW-CLIENT-C10B`
- Fallback if blocked: `RW-CLIENT-C10A`
- Verification scope: `./gradlew :RewriteClient:test :RewriteClient:uiTest`

## Completion Tracks
### RW-CLIENT-C0A - Login and desktop-entry MVC extraction
- Status: `done`
- Priority: `P1`
- Execution lane: `auth_bootstrap`
- Worker role: `worker`
- Depends on: `RW-CLIENT-002`, `RW-CLIENT-X1`
- Ready when: strict MVC base types are available
- Parallel with: `RW-CLIENT-C1A`, `RW-CLIENT-004B`
- Allowed write scope: `src/RewriteClient/**/auth/**`, `src/RewriteClient/**/desktop/**`, `src/RewriteClient/**/login/**`, `src/RewriteClient/**/shell/RewriteShellChromeBindingController.kt`, `src/RewriteClient/**/RewriteRootFrame.kt`, `src/RewriteClient/src/test/**`, `src/RewriteClient/src/uiTest/**`, `plans/rewrite/client/PLAN.md`
- Autonomous next: `RW-CLIENT-C0B`
- Fallback if blocked: `RW-CLIENT-C1A`
- Verification scope: `./gradlew :RewriteClientDev:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-C0B - Login and desktop-entry legacy parity rebuild
- Status: `done`
- Priority: `P1`
- Execution lane: `auth_bootstrap`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C0A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, look and feel, and IPv4 identity cleanup are available
- Parallel with: `RW-CLIENT-C1B`
- Allowed write scope: `src/RewriteClient/**/auth/**`, `src/RewriteClient/**/login/**`, `src/RewriteClient/resources/**`, `src/RewriteClient/src/test/**`, `src/RewriteClient/src/uiTest/**`, `plans/rewrite/client/PLAN.md`
- Autonomous next: `RW-CLIENT-C0C`
- Fallback if blocked: `RW-CLIENT-X3`
- Verification scope: `./gradlew :RewriteClientDev:test :RewriteClient:test :RewriteClient:uiTest`

### RW-CLIENT-C0C - Login and desktop-entry screenshot closure
- Status: `done`
- Priority: `P1`
- Execution lane: `auth_bootstrap`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C0B`, `RW-CLIENT-X4`
- Ready when: parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C1C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `src/RewriteClientDev/**`, `artifacts/rewrite/**`, `plans/rewrite/client/PLAN.md`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-GS-T6`
- Fallback if blocked: `RW-TEST-007`
- Verification scope: screenshot baseline approval, deterministic login/startup artifact capture

### RW-CLIENT-C1A - Shell chrome, stats rail, and taskbar MVC extraction
- Status: `done`
- Priority: `P0`
- Execution lane: `shell_chrome`
- Worker role: `worker`
- Depends on: `RW-CLIENT-003A`, `RW-CLIENT-003B1`, `RW-CLIENT-X1`
- Ready when: strict MVC base types exist and shell foundation is landed
- Parallel with: `RW-CLIENT-C0A`, `RW-CLIENT-X2`
- Allowed write scope: `src/RewriteClient/**/shell/**`, `src/RewriteClient/**/desktop/**`
- Autonomous next: `RW-CLIENT-C0A`
- Fallback if blocked: `RW-CLIENT-003B2`
- Verification scope: controller tests for shell state derivation, ui workflow tests for taskbar and focus routing

### RW-CLIENT-C1B - Shell chrome, stats rail, and taskbar legacy parity rebuild
- Status: `blocked`
- Priority: `P0`
- Execution lane: `shell_chrome`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C1A`, `RW-CLIENT-003B2`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: shell MVC extraction, bottom-message routing, look and feel, and IPv4 cleanup are available
- Parallel with: `RW-CLIENT-C2B`
- Allowed write scope: `src/RewriteClient/**/shell/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C1C`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: legacy shell screenshot comparisons, shell workflow tests, deterministic dev-mode shell audit

### RW-CLIENT-C1C - Shell chrome, stats rail, and taskbar screenshot closure
- Status: `todo`
- Priority: `P0`
- Execution lane: `shell_chrome`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C1B`, `RW-CLIENT-X4`
- Ready when: shell parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C0C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C2A`
- Fallback if blocked: `RW-TEST-007`
- Verification scope: approved shell and stats screenshot baselines, workflow and artifact pack closure

### RW-CLIENT-C2A - Economy windows MVC extraction
- Status: `todo`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W1A`, `RW-CLIENT-W1B`, `RW-CLIENT-X1`
- Ready when: banking and bounty foundations exist and strict MVC base types are available
- Parallel with: `RW-CLIENT-C3A`
- Allowed write scope: `src/RewriteClient/**/economy/**`
- Autonomous next: `RW-CLIENT-C2B`
- Fallback if blocked: `RW-CLIENT-C3A`
- Verification scope: controller tests for economy dialogs, workflow tests for banking and bounty actions

### RW-CLIENT-C2B - Economy windows legacy parity rebuild
- Status: `todo`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C2A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, look and feel, and IPv4 cleanup are available
- Parallel with: `RW-CLIENT-C3B`
- Allowed write scope: `src/RewriteClient/**/economy/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C2C`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: legacy banking and bounty screenshot comparisons, workflow tests

### RW-CLIENT-C2C - Economy windows screenshot closure
- Status: `todo`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C2B`, `RW-CLIENT-X4`, `RW-TEST-009`
- Ready when: parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C3C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C3A`
- Fallback if blocked: `RW-TEST-009`
- Verification scope: approved banking and bounty screenshot baselines

### RW-CLIENT-C3A - Files, chooser, file view, script editor, and image viewer MVC extraction
- Status: `todo`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2A`, `RW-CLIENT-W2B1`, `RW-CLIENT-W2B2`, `RW-CLIENT-W2B3`, `RW-CLIENT-X1`
- Ready when: all retained local file foundations exist and strict MVC base types are available
- Parallel with: `RW-CLIENT-C2A`, `RW-CLIENT-C4A`
- Allowed write scope: `src/RewriteClient/**/files/**`
- Autonomous next: `RW-CLIENT-C3B`
- Fallback if blocked: `RW-CLIENT-W2B3`
- Verification scope: controller tests for file browsing and edit/save flows, workflow tests for chooser and viewer actions

### RW-CLIENT-C3B - Files, chooser, file view, script editor, and image viewer legacy parity rebuild
- Status: `todo`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C3A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, look and feel, and IPv4 cleanup are available
- Parallel with: `RW-CLIENT-C4B`
- Allowed write scope: `src/RewriteClient/**/files/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C3C`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: legacy home, chooser, file-view, and editor screenshot comparisons

### RW-CLIENT-C3C - Files, chooser, file view, script editor, and image viewer screenshot closure
- Status: `todo`
- Priority: `P1`
- Execution lane: `economy_files`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C3B`, `RW-CLIENT-X4`, `RW-TEST-009`
- Ready when: parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C2C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C4A`
- Fallback if blocked: `RW-TEST-009`
- Verification scope: approved files and editor screenshot baselines

### RW-CLIENT-C4A - Shop FTP and Public FTP MVC extraction
- Status: `todo`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W2C1`, `RW-CLIENT-X1`
- Ready when: retained FTP foundation exists and strict MVC base types are available
- Parallel with: `RW-CLIENT-C5A`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/**/files/**`
- Autonomous next: `RW-CLIENT-C4B`
- Fallback if blocked: `RW-CLIENT-C5A`
- Verification scope: controller tests for shell-launched FTP windows, workflow tests for seller and public browsing flows

### RW-CLIENT-C4B - FTP legacy parity rebuild and retained transfer or password follow-up
- Status: `blocked`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C4A`, `RW-CLIENT-W2C2`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, FTP transfer/password transport, look and feel, and IPv4 cleanup are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C4C`
- Fallback if blocked: `RW-CLIENT-C5A`
- Verification scope: legacy shop FTP and public FTP screenshot comparisons, transfer and password workflow tests

### RW-CLIENT-C4C - FTP screenshot closure
- Status: `todo`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C4B`, `RW-CLIENT-X4`, `RW-TEST-009`
- Ready when: FTP parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C5C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C5A`
- Fallback if blocked: `RW-TEST-009`
- Verification scope: approved FTP screenshot baselines and artifact pack

### RW-CLIENT-C5A - Browser, store, site editor, help, and tutorial MVC extraction
- Status: `todo`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W3A`, `RW-CLIENT-W3B`, `RW-CLIENT-W3C1`, `RW-CLIENT-X1`
- Ready when: retained browser and site foundations exist and strict MVC base types are available
- Parallel with: `RW-CLIENT-C4A`
- Allowed write scope: `src/RewriteClient/**/web/**`
- Autonomous next: `RW-CLIENT-C5B`
- Fallback if blocked: `RW-CLIENT-W3C2`
- Verification scope: controller tests for browser/store/site editor flows, workflow tests for preview/save/purchase actions

### RW-CLIENT-C5B - Browser, store, site editor, help, and tutorial legacy parity rebuild
- Status: `blocked`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C5A`, `RW-CLIENT-W3C2`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, retained help/tutorial surfaces, look and feel, and IPv4 cleanup are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/web/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C5C`
- Fallback if blocked: `RW-CLIENT-C5A`
- Verification scope: legacy browser, site editor, help, and tutorial screenshot comparisons

### RW-CLIENT-C5C - Browser, store, site editor, help, and tutorial screenshot closure
- Status: `todo`
- Priority: `P1`
- Execution lane: `ftp_web`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C5B`, `RW-CLIENT-X4`, `RW-TEST-009`
- Ready when: browser/help parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C4C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C6A`
- Fallback if blocked: `RW-TEST-009`
- Verification scope: approved browser, store, site editor, help, and tutorial screenshot baselines

### RW-CLIENT-C6A - Network and Port Scan MVC extraction
- Status: `todo`
- Priority: `P0`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W5A`, `RW-CLIENT-X1`
- Ready when: retained network and scan foundation exists and strict MVC base types are available
- Parallel with: `RW-CLIENT-C7A`, `RW-CLIENT-C8A`
- Allowed write scope: `src/RewriteClient/**/network/**`
- Autonomous next: `RW-CLIENT-C6B`
- Fallback if blocked: `RW-CLIENT-C7A`
- Verification scope: controller tests for network selectors and scan workflows, UI tests for map and scan actions

### RW-CLIENT-C6B - Network and Port Scan legacy parity rebuild
- Status: `todo`
- Priority: `P0`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C6A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, look and feel, and IPv4 cleanup are available
- Parallel with: `RW-CLIENT-C7B`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C6C`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: legacy Network and Port Scan screenshot comparisons using `MapPanel`, `NetworkPanel`, `NetworkInfoPanel`, `NetworkMapPanel`, `PortScan`, `PortScanTableModel`, and `PortScanTableCellRenderer`

### RW-CLIENT-C6C - Network and Port Scan screenshot closure
- Status: `todo`
- Priority: `P0`
- Execution lane: `systems_network`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C6B`, `RW-CLIENT-X4`, `RW-TEST-008`
- Ready when: network and scan parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C7C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C7A`
- Fallback if blocked: `RW-TEST-008`
- Verification scope: approved Network and Port Scan screenshot baselines

### RW-CLIENT-C7A - Port Management MVC extraction
- Status: `todo`
- Priority: `P0`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W4A`, `RW-CLIENT-X1`
- Ready when: retained Port Management foundation exists and strict MVC base types are available
- Parallel with: `RW-CLIENT-C6A`, `RW-CLIENT-C8A`
- Allowed write scope: `src/RewriteClient/**/systems/**`
- Autonomous next: `RW-CLIENT-C7B`
- Fallback if blocked: `RW-CLIENT-C8A`
- Verification scope: controller tests for port detail models and action routing, workflow tests for selection and install flows

### RW-CLIENT-C7B - Port Management legacy parity rebuild
- Status: `todo`
- Priority: `P0`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C7A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, look and feel, and IPv4 cleanup are available
- Parallel with: `RW-CLIENT-C8B`
- Allowed write scope: `src/RewriteClient/**/systems/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C7C`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: legacy Port Management parity rebuild using `PortManagement` and `PortManagementMouseListener`, including tabbed per-port composition and status graphics

### RW-CLIENT-C7C - Port Management screenshot closure
- Status: `todo`
- Priority: `P0`
- Execution lane: `systems_network`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C7B`, `RW-CLIENT-X4`, `RW-TEST-008`
- Ready when: Port Management parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C6C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C8A`
- Fallback if blocked: `RW-TEST-008`
- Verification scope: approved Port Management screenshot baselines

### RW-CLIENT-C8A - Equipment, Firewall, and Watch manager MVC extraction
- Status: `todo`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W4B`, `RW-CLIENT-W4C`, `RW-CLIENT-X1`
- Ready when: retained inventory foundations exist and strict MVC base types are available
- Parallel with: `RW-CLIENT-C6A`, `RW-CLIENT-C7A`
- Allowed write scope: `src/RewriteClient/**/systems/**`
- Autonomous next: `RW-CLIENT-C8B`
- Fallback if blocked: `RW-CLIENT-C7A`
- Verification scope: controller tests for equipment/firewall/watch row models and action routing, UI workflow tests for install and watch edits

### RW-CLIENT-C8B - Equipment, Firewall, and Watch manager legacy parity rebuild
- Status: `todo`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C8A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, look and feel, and IPv4 cleanup are available
- Parallel with: `RW-CLIENT-C7B`
- Allowed write scope: `src/RewriteClient/**/systems/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C8C`
- Fallback if blocked: `RW-CLIENT-X2`
- Verification scope: legacy Equipment Manager, Firewall Manager, and Watch Manager screenshot comparisons

### RW-CLIENT-C8C - Equipment, Firewall, and Watch manager screenshot closure
- Status: `todo`
- Priority: `P1`
- Execution lane: `systems_network`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C8B`, `RW-CLIENT-X4`, `RW-TEST-008`
- Ready when: inventory parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C7C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C9A`
- Fallback if blocked: `RW-TEST-008`
- Verification scope: approved equipment, firewall, and watch screenshot baselines

### RW-CLIENT-C9A - Attack, Redirect, show_choices, and Zombie Attack MVC extraction
- Status: `todo`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W5B1`, `RW-CLIENT-W5B2`, `RW-CLIENT-W5C`, `RW-CLIENT-X1`
- Ready when: retained combat foundations exist and strict MVC base types are available
- Parallel with: `RW-CLIENT-C8A`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/**/combat/**`
- Autonomous next: `RW-CLIENT-C9B`
- Fallback if blocked: `RW-CLIENT-C8A`
- Verification scope: controller tests for combat session models and correlation, workflow tests for attack, redirect, choices, and zombie panes

### RW-CLIENT-C9B - Attack, Redirect, show_choices, and Zombie Attack legacy parity rebuild
- Status: `blocked`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C9A`, `RW-GS-T6`, `RW-GS-T7`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, retained combat transport completion, look and feel, and IPv4 cleanup are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/network/**`, `src/RewriteClient/**/combat/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C9C`
- Fallback if blocked: `RW-CLIENT-C9A`
- Verification scope: legacy Attack, Redirect, Choices, and Zombie Attack screenshot comparisons and workflow packs

### RW-CLIENT-C9C - Attack, Redirect, show_choices, and Zombie Attack screenshot closure
- Status: `todo`
- Priority: `P1`
- Execution lane: `combat_runtime`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C9B`, `RW-CLIENT-X4`, `RW-TEST-010`
- Ready when: combat parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C10C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C10A`
- Fallback if blocked: `RW-TEST-010`
- Verification scope: approved combat runtime screenshot baselines and transcript artifact capture

### RW-CLIENT-C10A - Preferences, Log Window, and Personal Settings MVC extraction
- Status: `todo`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W7A`, `RW-CLIENT-X1`
- Ready when: retained utility foundation exists and strict MVC base types are available
- Parallel with: `RW-CLIENT-C11A`
- Allowed write scope: `src/RewriteClient/**/utilities/**`
- Autonomous next: `RW-CLIENT-C10B`
- Fallback if blocked: `RW-CLIENT-W7B`
- Verification scope: controller tests for preferences/log models and startup preference routing, UI workflow tests for apply and log rendering

### RW-CLIENT-C10B - Preferences, Log Window, and Personal Settings legacy parity rebuild
- Status: `blocked`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C10A`, `RW-CLIENT-W7B`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: MVC extraction, retained Personal Settings transport, look and feel, and IPv4 cleanup are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/utilities/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C10C`
- Fallback if blocked: `RW-CLIENT-C10A`
- Verification scope: legacy Preferences, Log Window, and Personal Settings screenshot comparisons and startup behavior pack

### RW-CLIENT-C10C - Preferences, Log Window, and Personal Settings screenshot closure
- Status: `todo`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C10B`, `RW-CLIENT-X4`, `RW-TEST-011`
- Ready when: utility parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C9C`, `RW-CLIENT-C11C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `RW-CLIENT-C11A`
- Fallback if blocked: `RW-TEST-011`
- Verification scope: approved utility screenshot baselines and startup artifact capture

### RW-CLIENT-C11A - Client chat MVC extraction
- Status: `blocked`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-W6`, `RW-CLIENT-X1`
- Ready when: retained client chat foundation exists and strict MVC base types are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/chat/**`
- Autonomous next: `RW-CLIENT-C11B`
- Fallback if blocked: `RW-CLIENT-C10A`
- Verification scope: controller tests for channel and relation models, client chat workflow tests

### RW-CLIENT-C11B - Client chat legacy parity rebuild
- Status: `blocked`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `worker`
- Depends on: `RW-CLIENT-C11A`, `RW-CLIENT-X2`, `RW-CLIENT-X3`
- Ready when: client chat MVC extraction, look and feel, and IPv4 cleanup are available
- Parallel with: `none`
- Allowed write scope: `src/RewriteClient/**/chat/**`, `src/RewriteClient/resources/**`
- Autonomous next: `RW-CLIENT-C11C`
- Fallback if blocked: `RW-CLIENT-C10A`
- Verification scope: legacy client chat screenshot comparisons and workflow packs

### RW-CLIENT-C11C - Client chat screenshot closure
- Status: `todo`
- Priority: `P2`
- Execution lane: `utilities_chat`
- Worker role: `verifier`
- Depends on: `RW-CLIENT-C11B`, `RW-CLIENT-X4`, `RW-TEST-011`
- Ready when: client chat parity rebuild and screenshot harness glue are complete
- Parallel with: `RW-CLIENT-C10C`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`
- Autonomous next: `none`
- Fallback if blocked: `RW-TEST-011`
- Verification scope: approved client chat screenshot baselines and artifact capture

## Global Client Acceptance Gates
- No retained client family is `done` until its foundation card, MVC extraction card, legacy parity rebuild card, and screenshot closure card are all `done`.
- `RW-CLIENT-W2C2`, `RW-CLIENT-W3C2`, `RW-CLIENT-W6`, and `RW-CLIENT-W7B` may not be advanced without their named upstream retained tasks.
- The coordinator should saturate parallel work in this order when write scopes are disjoint: `RW-CLIENT-X1`, `RW-CLIENT-X3`, `RW-TEST-005`, `RW-M2-001`, then the next ready completion cards from the earliest unfinished pass.
