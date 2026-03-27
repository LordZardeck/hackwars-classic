# HackWars Rewrite Master Plan

## Status Dashboard
- Active mode: `autonomous_rewrite_execution`
- Current pass: `Pass 6 - Utilities, help/tutorial, personal settings, and client chat`
- Current milestone focus: `M8 - Client MVC and parity recovery foundations`
- Coordinator stop rule: continue until `M11` is `done`, or until the only remaining work is `blocked_external`
- Parallel saturation target: `8` active subagents, or the maximum safe non-overlapping count when fewer than `8` disjoint tasks exist

## Scope
- This plan governs the retained rewrite program across game server, client, chat server, persistence, testing, and evidence tracking.
- Hacktendo is out of rewrite scope everywhere.
- Command Prompt is out of rewrite client scope everywhere.
- Exact legacy parity, strict MVC, deterministic verification, and real dotted-quad IPv4 identity are locked completion gates for the retained rewrite client.

## Autonomous Execution Contract
- The coordinator must continue automatically until `M11` is `done`.
- User questions are forbidden unless there is a true external blocker: missing credentials, contradictory locked decisions across plan files, or an unrecoverable external system failure.
- Ready work is selected from the earliest unfinished milestone first, then by priority within that milestone, then by the smallest safe write scope, then by the highest unblock count.
- The coordinator must keep as many subagents active as safely possible, up to `8`, and must refill idle capacity immediately after a task commits or becomes blocked.
- No task may remain generically `blocked`; every blocked task must name exact upstream rewrite task IDs, or use `blocked_external` for a true outside-the-repo dependency.
- One green commit is required per completed task card, and the corresponding plan status update must be included in the same commit.
- The coordinator must continue to the next ready task immediately after each green commit.
- Worker subagents may not own overlapping write scopes in the same scheduling wave.
- Verifier subagents may overlap read-only with active workers.
- The coordinator is non-compliant if it leaves safe parallel capacity unused for more than one scheduling cycle.

## Status Vocabulary
- `todo`: defined work with incomplete prerequisites
- `ready`: unblocked work that can be started immediately
- `in_progress`: actively owned by the coordinator or a subagent
- `blocked`: waiting on exact upstream rewrite task IDs
- `blocked_external`: waiting on a true external dependency outside the repo
- `done`: implemented, verified, and committed
- `removed`: intentionally dropped from retained rewrite scope

## Module Graph
- Master coordination: [plans/rewrite/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/PLAN.md)
- Client recovery and parity: [plans/rewrite/client/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/client/PLAN.md)
- Game-server retained transport and runtime: [plans/rewrite/game-server/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/game-server/PLAN.md)
- Chat-server retained stack: [plans/rewrite/chat-server/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/chat-server/PLAN.md)
- Persistence and importer: [plans/rewrite/data/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/data/PLAN.md)
- Feature evidence and parity inventory: [plans/rewrite/feature-inventory/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/feature-inventory/PLAN.md)
- Verification packs and gates: [plans/rewrite/testing/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/testing/PLAN.md)

## Ready-Queue Policy
- The coordinator always selects the next task from the earliest unfinished milestone on the Milestone Board.
- Within a milestone, `ready` tasks are ordered by `Priority`, then by the number of blocked downstream tasks they unlock, then by write-scope size.
- When multiple `ready` tasks have disjoint write scopes, the coordinator should schedule them in parallel until either `8` subagents are active or the ready queue is exhausted.
- When a task becomes `blocked`, the coordinator must immediately schedule its `Autonomous next` fallback if it is `ready`.
- A milestone remains `in_progress` until every retained task mapped to it is `done`, `removed`, or `blocked_external`.

## Task Card Template

### RW-XXX - Title
- Status: `todo|ready|in_progress|blocked|blocked_external|done|removed`
- Priority: `P0|P1|P2|P3`
- Execution lane: `lane_name`
- Worker role: `explorer|worker|verifier|coordinator-owned`
- Depends on: `none`
- Ready when: concrete prerequisite statement using exact task IDs
- Parallel with: exact task IDs or `none`
- Allowed write scope: concrete repo paths or `none`
- Autonomous next: exact next task ID after completion
- Fallback if blocked: exact next task ID or `none`
- Verification scope: exact commands, artifacts, or audit checks

## Autonomous Pass Board
| Pass | Goal | Primary plan files | Ready queue seed | Max safe workers |
| --- | --- | --- | --- | --- |
| `Pass 0` | Rewrite plans to the autonomous schema and remove stale scope | `PLAN.md`, `client/PLAN.md`, `game-server/PLAN.md`, `chat-server/PLAN.md`, `data/PLAN.md`, `feature-inventory/PLAN.md`, `testing/PLAN.md` | `RW-M0-003` | `1` |
| `Pass 1` | Land cross-cutting foundations for MVC, look and feel, IPv4 identity, screenshot harness, and evidence closure | `client/PLAN.md`, `testing/PLAN.md`, `feature-inventory/PLAN.md` | `RW-CLIENT-X1`, `RW-CLIENT-X2`, `RW-CLIENT-X3`, `RW-CLIENT-X4`, `RW-TEST-005`, `RW-TEST-006` | `6` |
| `Pass 2` | Recover login, shell chrome, stats rail, taskbar, and deterministic dev harness parity | `client/PLAN.md`, `testing/PLAN.md` | `RW-CLIENT-C0A`, `RW-CLIENT-C1A`, `RW-CLIENT-004B`, `RW-TEST-007` | `4` |
| `Pass 3` | Recover network and system surfaces with MVC and legacy parity | `client/PLAN.md`, `testing/PLAN.md` | `RW-CLIENT-C6A`, `RW-CLIENT-C7A`, `RW-CLIENT-C8A1`, `RW-CLIENT-C8A2`, `RW-TEST-008` | `5` |
| `Pass 4` | Recover economy, files, FTP, browser, store, and editor families | `client/PLAN.md`, `game-server/PLAN.md`, `testing/PLAN.md` | `RW-CLIENT-C2A`, `RW-CLIENT-C3A`, `RW-CLIENT-C4A`, `RW-CLIENT-C5A`, `RW-TEST-009` | `8` |
| `Pass 5` | Recover combat and runtime panes, plus any missing server transport they require | `client/PLAN.md`, `game-server/PLAN.md`, `testing/PLAN.md` | `RW-CLIENT-C9A`, `RW-GS-T6`, `RW-GS-T7`, `RW-TEST-010` | `8` |
| `Pass 6` | Recover utilities, help/tutorial, personal settings, and client chat | `client/PLAN.md`, `game-server/PLAN.md`, `chat-server/PLAN.md`, `testing/PLAN.md` | `RW-CLIENT-C10A`, `RW-CLIENT-W3C2`, `RW-CLIENT-W7B`, `RW-CHAT-001A`, `RW-TEST-011` | `8` |
| `Pass 7` | Close retained blockers, run full integration, and complete parity audit | `client/PLAN.md`, `game-server/PLAN.md`, `chat-server/PLAN.md`, `data/PLAN.md`, `feature-inventory/PLAN.md`, `testing/PLAN.md` | `RW-GS-T1`, `RW-GS-T2`, `RW-GS-T3`, `RW-GS-T4`, `RW-GS-T5`, `RW-TEST-012` | `8` |

## Milestone Board
| Milestone | Status | Exit criteria |
| --- | --- | --- |
| `M0` Planning normalization | `done` | All rewrite plan files use the autonomous task-card schema, the pass board is encoded, and no retained task uses vague blockers. |
| `M1` Build and module foundations | `done` | Rewrite modules, Gradle conventions, and dependency guardrails are stable and green. |
| `M2` Evidence and inventory closure | `done` | Feature inventory rows are complete for retained scope, every row has legacy references and parity acceptance, and known gaps are tracked without ambiguity. |
| `M3` Transport and offline harness foundations | `done` | Frame codec, fake auth, deterministic transport harness, and dev bootstrap seams exist and stay green. |
| `M4` Persistence and importer foundations | `done` | Canonical schema slices, importer scaffolding, and migration validation are complete for retained scope. |
| `M5` Game-core and retained runtime foundations | `done` | Typed contracts, scheduler/runtime basics, and retained server foundations are implemented and verified. |
| `M6` Retained game-server transport completion | `done` | Every retained client blocker that needs new GAME transport has a concrete game-server task and verified implementation. |
| `M7` Chat-server completion | `in_progress` | Chat contracts, bootstrap, channels, moderation, messaging, relations, and parity events are implemented and verified. |
| `M8` Client MVC and parity recovery foundations | `in_progress` | Strict MVC framework, look and feel, parity assets, IPv4 identity cleanup, screenshot harness, and shell/login recovery are in place. `Base MVC` means view-only Swing classes, immutable view models, and controller-owned listeners/state wiring. |
| `M9` Retained client family parity completion | `todo` | Every retained client family reaches screenshot-backed legacy parity. `Ported` means screenshot-backed visual parity with deterministic workflow tests, not merely a functioning Kotlin window. |
| `M10` Integration and blocker closure | `todo` | All retained blocked tasks are resolved or marked `blocked_external`, end-to-end rewrite flows are green, and feature inventory evidence is complete. |
| `M11` Final parity audit and release closure | `todo` | The retained rewrite surface passes deterministic parity audit, MVC guardrails, UI workflow packs, and integration verification with no unresolved retained blockers. |

## Historical Foundation Tasks
### RW-M0-001 - Create rewrite plan document set
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `coordinator-owned`
- Depends on: `none`
- Ready when: rewrite planning starts
- Parallel with: `none`
- Allowed write scope: `plans/rewrite/**`
- Autonomous next: `RW-M0-002`
- Fallback if blocked: `none`
- Verification scope: `test -f plans/rewrite/PLAN.md`

### RW-M0-002 - Reset rewrite plans for strict MVC, parity, and real IPv4 identity
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `coordinator-owned`
- Depends on: `RW-M0-001`
- Ready when: rewrite planning exists and client recovery rules need to be locked
- Parallel with: `none`
- Allowed write scope: `plans/rewrite/**`
- Autonomous next: `RW-M0-003`
- Fallback if blocked: `none`
- Verification scope: `rg -n "strict MVC|legacy parity|IPv4" plans/rewrite`

### RW-M1-001 - Add rewrite module graph to Gradle
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `worker`
- Depends on: `RW-M0-001`
- Ready when: rewrite modules can be modeled explicitly
- Parallel with: `RW-M1-002`
- Allowed write scope: `settings.gradle`, `build.gradle`, `src/**/build.gradle*`
- Autonomous next: `RW-M1-002`
- Fallback if blocked: `RW-M1-003`
- Verification scope: `./gradlew rewriteCheck`

### RW-M1-002 - Enforce no-Java and no-legacy runtime dependency rules
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `worker`
- Depends on: `RW-M1-001`
- Ready when: rewrite module graph exists
- Parallel with: `RW-M1-003`
- Allowed write scope: `build-logic/**`, `gradle/**`, `build.gradle*`
- Autonomous next: `RW-M1-003`
- Fallback if blocked: `none`
- Verification scope: `./gradlew rewriteNoJava rewriteNoLegacyDeps`

### RW-M1-003 - Stabilize rewrite build conventions
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `worker`
- Depends on: `RW-M1-001`
- Ready when: rewrite module graph and dependency rules exist
- Parallel with: `RW-M1-002`
- Allowed write scope: `build-logic/**`, `gradle/**`, `build.gradle*`
- Autonomous next: `RW-M2-001`
- Fallback if blocked: `none`
- Verification scope: `./gradlew rewriteCheck`

### RW-PROTO-001 - Freeze auth and bootstrap frame contract
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `worker`
- Depends on: `RW-M1-003`
- Ready when: retained rewrite protocol module is stable
- Parallel with: `RW-PROTO-002`
- Allowed write scope: `src/RewriteProtocol/**`
- Autonomous next: `RW-PROTO-002`
- Fallback if blocked: `RW-PROTO-003`
- Verification scope: `./gradlew :RewriteProtocol:test`

### RW-PROTO-002 - Freeze correlated callback and event envelope contract
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `worker`
- Depends on: `RW-PROTO-001`
- Ready when: bootstrap frame contract exists
- Parallel with: `RW-PROTO-003`
- Allowed write scope: `src/RewriteProtocol/**`
- Autonomous next: `RW-PROTO-003`
- Fallback if blocked: `none`
- Verification scope: `./gradlew :RewriteProtocol:test`

### RW-PROTO-003 - Freeze decoded GAME snapshot and section projection contract
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `worker`
- Depends on: `RW-PROTO-001`
- Ready when: rewrite protocol foundations exist
- Parallel with: `RW-PROTO-002`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteClientModel/**`
- Autonomous next: `RW-CLIENT-001A`
- Fallback if blocked: `none`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteClientModel:test`

## Coordination Tasks
### RW-M0-003 - Convert rewrite plans to the autonomous runbook schema
- Status: `done`
- Priority: `P0`
- Execution lane: `planning`
- Worker role: `coordinator-owned`
- Depends on: `RW-M0-002`
- Ready when: all seven rewrite plan files can be updated together
- Parallel with: `none`
- Allowed write scope: `plans/rewrite/**`
- Autonomous next: `RW-M2-001`
- Fallback if blocked: `none`
- Verification scope: `rg -n "Autonomous Execution Contract|Autonomous Pass Board|Priority|Execution lane|Worker role|Ready when|Parallel with|Autonomous next|Fallback if blocked|Verification scope|blocked_external" plans/rewrite`

### RW-M2-001 - Complete retained feature row evidence and dependency closure
- Status: `done`
- Priority: `P1`
- Execution lane: `evidence`
- Worker role: `worker`
- Depends on: `RW-M0-003`
- Ready when: the feature inventory schema includes foundation, completion, legacy reference, parity acceptance, and upstream blocker fields
- Parallel with: `RW-M2-002`, `RW-M2-003`, `RW-TEST-005`
- Allowed write scope: `plans/rewrite/feature-inventory/PLAN.md`, `plans/rewrite/client/PLAN.md`, `plans/rewrite/game-server/PLAN.md`, `plans/rewrite/chat-server/PLAN.md`
- Autonomous next: `RW-M2-004`
- Fallback if blocked: `RW-M2-003`
- Verification scope: `rg -n "Foundation task|Completion task|Legacy UI reference|Parity acceptance|Blocking upstream task" plans/rewrite/feature-inventory/PLAN.md`

### RW-M2-002 - Close client UI evidence gaps and legacy reference coverage
- Status: `done`
- Priority: `P1`
- Execution lane: `evidence`
- Worker role: `explorer`
- Depends on: `RW-M0-003`
- Ready when: the client recovery lanes and feature inventory rows both point at retained client families
- Parallel with: `RW-M2-001`, `RW-M2-003`
- Allowed write scope: `plans/rewrite/client/PLAN.md`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-M2-004`
- Fallback if blocked: `RW-M2-001`
- Verification scope: `rg -n "Legacy UI reference|Parity acceptance" plans/rewrite/client/PLAN.md plans/rewrite/feature-inventory/PLAN.md`

### RW-M2-003 - Close retained chat, data, and protocol evidence gaps
- Status: `done`
- Priority: `P2`
- Execution lane: `evidence`
- Worker role: `explorer`
- Depends on: `RW-M0-003`
- Ready when: chat-server, data, and testing plans all use the autonomous schema
- Parallel with: `RW-M2-001`, `RW-M2-002`
- Allowed write scope: `plans/rewrite/chat-server/PLAN.md`, `plans/rewrite/data/PLAN.md`, `plans/rewrite/testing/PLAN.md`
- Autonomous next: `RW-M2-004`
- Fallback if blocked: `RW-M2-001`
- Verification scope: `rg -n "Execution lane|Worker role|Verification scope" plans/rewrite/chat-server/PLAN.md plans/rewrite/data/PLAN.md plans/rewrite/testing/PLAN.md`

### RW-M2-004 - Track retained parity gaps and unblock map without vague blockers
- Status: `done`
- Priority: `P1`
- Execution lane: `evidence`
- Worker role: `coordinator-owned`
- Depends on: `RW-M2-001`, `RW-M2-002`, `RW-M2-003`
- Ready when: all retained feature rows and downstream plan cards name exact completion and blocker IDs
- Parallel with: `none`
- Allowed write scope: `plans/rewrite/**`
- Autonomous next: `RW-CLIENT-X1`
- Fallback if blocked: `RW-TEST-005`
- Verification scope: `rg -n "blocked on future|deferred for later|later follow-up" plans/rewrite`

## Global Acceptance Gates
- No retained milestone may be marked `done` unless every retained task mapped to it is `done`, `removed`, or `blocked_external`.
- No retained client family may be marked `done` without green controller tests, UI workflow tests, screenshot parity tests, MVC guardrail coverage, and valid IPv4 identity in deterministic fixtures.
- The coordinator only stops when every retained milestone is `done`, or the remaining queue is exclusively `blocked_external`.

## Glossary
- `Legacy parity`: deterministic rewrite UI that matches the retained legacy Swing surface in layout, chrome, assets, wording, interaction flow, and screenshot output.
- `Strict MVC`: view-only Swing classes, immutable view models, and controller-owned event wiring, routing, selector subscriptions, and lifecycle management.
- `Retained scope`: rewrite functionality that remains in plan after removing Hacktendo and Command Prompt.
