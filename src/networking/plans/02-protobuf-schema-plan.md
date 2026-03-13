# Plan 02: Protobuf Contract Definition

## Goal
Define protobuf schemas for all current client<->game/chat communication audited in Plan 00.

## Chunk 1: Split Schema Files by Domain
- Create:
  - `auth.proto` (JWT handshake + auth responses)
  - `common.proto` (errors, paging, money/quantity wrappers, metadata)
  - `game_commands.proto` (client -> game)
  - `game_updates.proto` (game -> client)
  - `chat_messages.proto` (chat in/out)
- Keep a single package namespace `hackwars.networking.v1`.
- Done when: all schema files compile with `./gradlew :Networking:generateProto`.

## Chunk 2: Define Game Command Messages
- Replace stringly `RemoteFunctionCall(function, Object[])` with typed command messages.
- Group commands into typed oneofs:
  - account/session
  - filesystem/files
  - ports/equipment/watch
  - network/combat
  - web/store/economy
  - hacktendo
- Include a `LegacyCommand` fallback message for unsupported edge cases during migration.
- Done when: every function name listed in Plan 00 has a mapped command type or explicit legacy fallback.

## Chunk 3: Define Game Update Messages
- Model `PacketAssignment` and `DamageAssignment` as structured updates:
  - `GameSnapshotDelta`
  - `CombatDelta`
  - `DirectoryListing`
  - `FilePayload` (HackerFile model)
  - `PortState`, `WatchState`, `NetworkState`
  - `UiMessage`, `ChoiceSet`, `CaptchaPayload`
- Preserve optional/partial update behavior (sparse fields).
- Done when: there is a field mapping document for every `PacketAssignment`/`DamageAssignment` field.

## Chunk 4: Define Chat Messages
- Model `ArrayMessageIn/Out` as repeated typed messages.
- Cover all current chat classes:
  - all `MsgIn*` variants in Plan 00
  - all `MsgOut*` variants in Plan 00
- Include admin flags and relation online-state semantics.
- Done when: chat client/server can serialize and deserialize each current message type without Java serialization.

## Chunk 5: Versioning + Compatibility Rules
- Add schema governance doc:
  - field reservation rules
  - additive changes only for minor versions
  - required/optional behavior contract
- Reserve field numbers where legacy/removal is expected.
- Done when: schema lint/check task fails on breaking changes.

## Chunk 6: Codegen Integration + Descriptor Export
- Keep generated Java sources in `Networking` module only.
- Export descriptor set for non-Java consumers (`networking.protoset`).
- Done when: GameServer, ChatServer, and Client compile against generated classes with no direct dependency on legacy message classes for new transport.
