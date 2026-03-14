# Plan 02: Protobuf Contract Definition

## Goal
Define protobuf schemas for all current client<->game/chat communication audited in Plan 00.

## Chunk 1: Split Schema Files by Domain
- Create:
  - `v1/core.proto` (`Request` / `Response` wrappers with `google.protobuf.Any` payload)
  - `auth/v1/auth.proto` (JWT handshake + auth responses)
  - `game/v1/game.proto` (service-level game request/response payloads)
  - `chat/v1/chat.proto` (service-level chat request/response payloads)
- Keep package namespaces split by domain:
  - `hackwars.networking.v1`
  - `hackwars.networking.auth.v1`
  - `hackwars.networking.game.v1`
  - `hackwars.networking.chat.v1`
- Done when: all schema files compile with `./gradlew :Networking:generateProto`.

## Chunk 2: Define Game Command Messages
- Replace stringly `RemoteFunctionCall(function, Object[])` with typed command messages.
- Carry all typed game payloads inside `core.v1.Request.payload` (`Any`) and return via `core.v1.Response.payload`.
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
- Wrap server pushes/acks in `core.v1.Response` with typed game update payloads.
- Done when: there is a field mapping document for every `PacketAssignment`/`DamageAssignment` field.

## Chunk 4: Define Chat Messages
- Model `ArrayMessageIn/Out` as repeated typed messages.
- Carry chat request/response payloads via `core.v1.Request` / `core.v1.Response`.
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
