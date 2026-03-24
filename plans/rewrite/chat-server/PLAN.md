# Rewrite Chat Server Plan

## Scope
- Own all rewrite chat-session, channel, moderation, whisper, relation, and fanout behavior.
- Keep implementation intentionally small and isolated from game logic.

## Core Tasks
### RW-CHAT-001 - Define chat contracts and invariants
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-M1-003`
- Allowed write scope: `:RewriteChatCore`
- Verification command: `./gradlew :RewriteChatCore:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Freeze `ChatSession`, `ChannelId`, `RelationGraph`, `ChatEvent`, and moderation rules.
  - Capture auto-channel and timeout semantics from legacy evidence.

### RW-CHAT-002 - Implement session bootstrap, ping, timeout, forced logout
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CHAT-001`, `RW-PROTO-001`
- Allowed write scope: `:RewriteChatCore`, `:RewriteChatServer`
- Verification command: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Preserve verified PlayFab-ID-as-chat-identity behavior.
  - Preserve forced logout semantics.

### RW-CHAT-003 - Implement channel lifecycle and fanout
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CHAT-001`
- Allowed write scope: `:RewriteChatCore`, `:RewriteChatServer`
- Verification command: `./gradlew :RewriteChatCore:test :RewriteChatServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Auto-subscribe to required default channels.
  - Implement create, join, leave, password, kick, admin, and auto-delete behavior.

### RW-CHAT-004 - Implement relation graph, ignore, friend, mute, online notifications
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CHAT-001`, `RW-DATA-003`
- Allowed write scope: `:RewriteChatCore`, `:RewriteChatServer`, `:RewritePersistence`
- Verification command: `./gradlew :RewriteChatCore:test :RewriteChatServer:test :RewritePersistence:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Ignored users must be filtered from whisper and channel fanout.
  - Relation changes must push updates to affected clients.

### RW-CHAT-005 - Implement protocol parity events
- Status: `todo`
- Owner: `unassigned`
- Depends on: `RW-CHAT-002`, `RW-PROTO-002`
- Allowed write scope: `:RewriteProtocol`, `:RewriteChatCore`, `:RewriteChatServer`
- Verification command: `./gradlew :RewriteProtocol:test :RewriteChatServer:test`
- Artifacts: `build/reports/tests/test`
- Commit rule: `single green commit only`
- Notes:
  - Cover channel text, `/me`, whisper, relation list/add, join/leave/add/remove/kick, and errors.

## Verification Gates
- Chat tasks must link to feature rows in `plans/rewrite/feature-inventory/PLAN.md`.
- Chat integration tests must run without depending on legacy chat globals or legacy transports.
