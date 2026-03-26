# Rewrite Chat Server Plan

## Scope
- This plan covers the retained rewrite chat server and retained chat protocol/event work that backs the retained client chat roadmap.
- Chat work must be splittable into disjoint cards so it can run in parallel with retained client parity work.

## Locked Chat Rules
- Chat tasks must preserve the retained client-facing chat feature set: session bootstrap, channels, moderation, message fanout, whispers, relations, ignore or mute behavior, online notifications, and parity events.
- Every blocked chat task must name exact upstream rewrite task IDs from this file, [plans/rewrite/data/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/data/PLAN.md), or [plans/rewrite/testing/PLAN.md](/Users/lordzardeck/.codex/worktrees/47a1/hackwars-classic-original/plans/rewrite/testing/PLAN.md).

## Execution Lanes
| Lane | Scope | Primary write scope |
| --- | --- | --- |
| `contracts` | chat contracts, invariants, and protocol mapping | `src/RewriteProtocol/**`, `src/RewriteChatCore/**` |
| `bootstrap` | auth binding, sessions, ping, timeout, forced logout | `src/RewriteChatCore/**`, `src/RewriteChatServer/**` |
| `channels` | channel lifecycle, moderation, fanout, history | `src/RewriteChatCore/**`, `src/RewriteChatServer/**` |
| `social` | relations, ignore, friend, mute, notifications | `src/RewriteChatCore/**`, `src/RewriteChatServer/**`, `src/RewritePersistence/**` |

## Task Cards
### RW-CHAT-001A - Define canonical chat contracts and invariants
- Status: `ready`
- Priority: `P1`
- Execution lane: `contracts`
- Worker role: `worker`
- Depends on: `RW-M1-003`
- Ready when: retained rewrite module graph and protocol foundations are stable
- Parallel with: `RW-CHAT-001B`, `RW-DATA-003D`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteChatCore/**`
- Autonomous next: `RW-CHAT-001B`
- Fallback if blocked: `RW-DATA-003D`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteChatCore:test`

### RW-CHAT-001B - Map retained chat frames, payloads, and parity event catalog
- Status: `todo`
- Priority: `P1`
- Execution lane: `contracts`
- Worker role: `worker`
- Depends on: `RW-CHAT-001A`, `RW-PROTO-001`
- Ready when: canonical chat contracts exist
- Parallel with: `RW-CHAT-002A`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteChatCore/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-CHAT-002A`
- Fallback if blocked: `RW-CHAT-001A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteChatCore:test`

### RW-CHAT-002A - Implement retained chat session bootstrap and auth binding
- Status: `todo`
- Priority: `P1`
- Execution lane: `bootstrap`
- Worker role: `worker`
- Depends on: `RW-CHAT-001A`, `RW-DATA-003A`
- Ready when: canonical chat contracts and retained auth/session schema exist
- Parallel with: `RW-CHAT-003A`, `RW-CHAT-004A`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CHAT-002B`
- Fallback if blocked: `RW-DATA-003A`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test :RewritePersistence:test`

### RW-CHAT-002B - Implement retained chat ping, timeout, and forced logout lifecycle
- Status: `todo`
- Priority: `P2`
- Execution lane: `bootstrap`
- Worker role: `worker`
- Depends on: `RW-CHAT-002A`
- Ready when: retained chat session bootstrap is working
- Parallel with: `RW-CHAT-003B`, `RW-CHAT-004B`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`
- Autonomous next: `RW-CHAT-005A`
- Fallback if blocked: `RW-CHAT-002A`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`

### RW-CHAT-003A - Implement retained channel lifecycle and moderation model
- Status: `todo`
- Priority: `P1`
- Execution lane: `channels`
- Worker role: `worker`
- Depends on: `RW-CHAT-001A`, `RW-DATA-003D`
- Ready when: canonical chat contracts and retained chat/social schema exist
- Parallel with: `RW-CHAT-002A`, `RW-CHAT-004A`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CHAT-003B`
- Fallback if blocked: `RW-DATA-003D`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test :RewritePersistence:test`

### RW-CHAT-003B - Implement retained channel fanout, history, and whisper pipeline
- Status: `todo`
- Priority: `P1`
- Execution lane: `channels`
- Worker role: `worker`
- Depends on: `RW-CHAT-002A`, `RW-CHAT-003A`
- Ready when: retained session bootstrap and channel lifecycle both work
- Parallel with: `RW-CHAT-004B`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`
- Autonomous next: `RW-CHAT-005A`
- Fallback if blocked: `RW-CHAT-003A`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`

### RW-CHAT-004A - Implement retained relations, ignore, friend, and mute model
- Status: `todo`
- Priority: `P2`
- Execution lane: `social`
- Worker role: `worker`
- Depends on: `RW-CHAT-001A`, `RW-DATA-003D`
- Ready when: canonical chat contracts and retained chat/social schema exist
- Parallel with: `RW-CHAT-002A`, `RW-CHAT-003A`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`, `src/RewritePersistence/**`
- Autonomous next: `RW-CHAT-004B`
- Fallback if blocked: `RW-DATA-003D`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test :RewritePersistence:test`

### RW-CHAT-004B - Implement retained online notifications and relation-driven fanout
- Status: `todo`
- Priority: `P2`
- Execution lane: `social`
- Worker role: `worker`
- Depends on: `RW-CHAT-002A`, `RW-CHAT-004A`
- Ready when: retained chat sessions and relations graph are both working
- Parallel with: `RW-CHAT-003B`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`
- Autonomous next: `RW-CHAT-005A`
- Fallback if blocked: `RW-CHAT-004A`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`

### RW-CHAT-005A - Emit retained chat protocol parity events
- Status: `todo`
- Priority: `P1`
- Execution lane: `contracts`
- Worker role: `worker`
- Depends on: `RW-CHAT-001B`, `RW-CHAT-002B`, `RW-CHAT-003B`, `RW-CHAT-004B`
- Ready when: retained chat contract mapping, lifecycle, fanout, and relation notifications all work
- Parallel with: `RW-TEST-011`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteChatCore/**`, `src/RewriteChatServer/**`
- Autonomous next: `RW-CHAT-005B`
- Fallback if blocked: `RW-CHAT-003B`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteChatCore:test :RewriteChatServer:test`

### RW-CHAT-005B - Close retained client-chat verification pack
- Status: `todo`
- Priority: `P2`
- Execution lane: `social`
- Worker role: `verifier`
- Depends on: `RW-CHAT-005A`, `RW-TEST-011`
- Ready when: retained chat parity events exist and the pass-6 verification pack is available
- Parallel with: `RW-CLIENT-C11A`
- Allowed write scope: `src/RewriteChatServer/test/**`, `src/RewriteClient/src/uiTest/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-CLIENT-W6`
- Fallback if blocked: `RW-TEST-011`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test :RewriteClient:test :RewriteClient:uiTest`

## Global Acceptance Gates
- No retained chat task is `done` until protocol tests, server tests, and downstream retained client verification remain green.
- `RW-CLIENT-W6` may only advance after `RW-CHAT-003B`, `RW-CHAT-004B`, and `RW-CHAT-005A` are `done`.
