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
- Status: `done`
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
- Status: `done`
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

### RW-CHAT-001C - Correct retained add_admin and mute contract scoping
- Status: `done`
- Priority: `P1`
- Execution lane: `contracts`
- Worker role: `worker`
- Depends on: `RW-CHAT-001A`, `RW-CHAT-001B`
- Ready when: retained chat lifecycle routing exposes the channel-scoping mismatch for moderation commands
- Parallel with: `RW-CHAT-003B`, `RW-CHAT-004A`
- Allowed write scope: `src/RewriteProtocol/**`, `src/RewriteChatCore/**`, `src/RewriteChatServer/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-CHAT-003B`
- Fallback if blocked: `RW-CHAT-003A`
- Verification scope: `./gradlew :RewriteProtocol:test :RewriteChatCore:test :RewriteChatServer:test`
- Locked scope note: this card adds channel scope to retained `add_admin` and `mute` payloads, updates the retained chat adapter to route those requests, and closes the wire mismatch exposed during `RW-CHAT-003A`.

### RW-CHAT-002A - Implement retained chat session bootstrap and auth binding
- Status: `done`
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
- Status: `done`
- Priority: `P2`
- Execution lane: `bootstrap`
- Worker role: `worker`
- Depends on: `RW-CHAT-002A`
- Ready when: retained chat session bootstrap is working
- Parallel with: `RW-CHAT-003B`, `RW-CHAT-004B`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`
- Autonomous next: `RW-CHAT-004A`
- Fallback if blocked: `RW-CHAT-002A`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`
- Locked scope note: this card touches retained chat activity timestamps on inbound ping and command activity, preserves retained ping echo semantics, and verifies that idle timeout closure tears down persisted chat service-session and presence state.

### RW-CHAT-002C - Add retained chat outbound multi-recipient transport hook
- Status: `done`
- Priority: `P1`
- Execution lane: `bootstrap`
- Worker role: `worker`
- Depends on: `RW-CHAT-002A`
- Ready when: retained chat sessions can bind canonical player identities to active connections
- Parallel with: `RW-CHAT-004A`
- Allowed write scope: `src/RewriteChatServer/**`, `src/RewriteTestKit/**`
- Autonomous next: `RW-CHAT-003B`
- Fallback if blocked: `RW-CHAT-002A`
- Verification scope: `./gradlew --rerun-tasks :RewriteProtocol:test :RewriteChatCore:test :RewriteChatServer:test`
- Locked scope note: this card binds a transport push callback into the retained chat adapter so later channel and whisper fanout can target other authenticated chat connections instead of only the caller.

### RW-CHAT-003A - Implement retained channel lifecycle and moderation model
- Status: `done`
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
- Locked scope note: this card closes retained `sub_channels`, `channel_create`, `channel_join`, `channel_leave`, and `channel_kick`, plus actor-local roster refresh and empty-channel cleanup. Channel-scoped `add_admin` and `mute` contract/routing is closed in `RW-CHAT-001C`.

### RW-CHAT-003B - Implement retained channel fanout, history, and whisper pipeline
- Status: `done`
- Priority: `P1`
- Execution lane: `channels`
- Worker role: `worker`
- Depends on: `RW-CHAT-002A`, `RW-CHAT-002C`, `RW-CHAT-003A`
- Ready when: retained session bootstrap, outbound transport hook, and channel lifecycle all work
- Parallel with: `RW-CHAT-004B`
- Allowed write scope: `src/RewriteChatCore/**`, `src/RewriteChatServer/**`
- Autonomous next: `RW-CHAT-005A`
- Fallback if blocked: `RW-CHAT-003A`
- Verification scope: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`

### RW-CHAT-004A - Implement retained relations, ignore, friend, and mute model
- Status: `done`
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
- Locked scope note: this card closes retained `relation_list` and `relation_add`, including actor-local relation refresh, friend/ignore persistence reconciliation, retained comment payload storage, and single-kind delete support for boolean flag turnoff.

### RW-CHAT-004B - Implement retained online notifications and relation-driven fanout
- Status: `done`
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
- Locked scope note: this card closes retained reciprocal `relation_add` fanout on chat login/logout, including the legacy inconsistency where login treats any outgoing relation as eligible while logout only fans out from friend-marked outgoing relations.

### RW-CHAT-005A - Emit retained chat protocol parity events
- Status: `done`
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
- Locked scope note: this card closes the retained parity event surface for actor-local `channel_join` and `channel_leave`, joiner-inclusive `channel_add`, kicker-inclusive `channel_remove`, retained `channel_text_me` persistence/event coverage, and `!SYSTEM! You are now the channel admin` handoff text on create, leave, kick, and disconnect-driven ownership transfer.

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
