# Plan 04: ChatServer Migration (MessagePacket -> Protobuf)

## Goal
Move chat traffic from `MessageInPacket/MessageOutPacket` serialized payloads to protobuf while preserving channel, whisper, moderation, and relation behavior.

## Chunk 1: Build Chat Protobuf Session Endpoint
- Add chat protobuf endpoint with same auth gating as Plan 01.
- Keep current `MainServer` and `MsgProcessorThread` logic intact behind adapter boundary.
- Done when: authenticated chat client can connect and round-trip ping.

## Chunk 2: Inbound Chat Message Adapter
- Convert protobuf inbound messages into existing chat model operations:
  - channel text/join/leave/create/kick
  - whispers
  - moderation (`addAdmin`, `mute`)
  - relations (`relationList`, `relationAdd`)
  - subscription refresh (`subChannels`)
- Done when: each current `MsgIn*` class in Plan 00 has a protobuf equivalent and adapter mapping.

## Chunk 3: Outbound Chat Message Adapter
- Convert server-side outgoing events into protobuf:
  - `ChannelText`, `ChannelTextMe`
  - `ChannelJoin/Leave/Add/Remove/Kick`
  - `SubChannels`
  - `RelationList`, `RelationAdd`
  - `Whisper`, `Error`
- Done when: all currently emitted `MsgOut*` flows are represented in protobuf and delivered to client.

## Chunk 4: Client ChatController Integration
- Replace `MessageInPacket(ArrayMessageIn)` emission with protobuf chat send pipeline.
- Replace `ArrayMessageOut` processing path with protobuf receive dispatcher.
- Preserve UI semantics (`viewMain`, channel tab updates, admin markers).
- Done when: chat controller can operate with protobuf transport and no Java serialization dependency.

## Chunk 5: Chat Session Semantics Parity
- Preserve or explicitly redefine:
  - ping cadence/timeouts
  - reconnect behavior
  - relation online notifications
  - forced logout path semantics
- Done when: reconnect and timeout behavior matches expected gameplay UX.

## Chunk 6: Remove Legacy Chat Transport
- Remove chat-side Reporter path in `View` behind final cutover flag.
- Remove `MessageInPacket/MessageOutPacket` transport usage in runtime path.
- Done when: production chat traffic is protobuf-only.
