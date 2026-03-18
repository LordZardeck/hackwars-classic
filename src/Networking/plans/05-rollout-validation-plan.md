# Plan 05: Rollout, Verification, and Cutover

## Goal
Ship protobuf + JWT networking safely with measurable parity against current behavior.

## Chunk 1: Contract/Mapper Tests
- Add schema conformance tests for all protobuf messages.
- Add mapper tests:
  - game command -> existing `ApplicationData`
  - existing packet objects -> protobuf updates
  - chat message classes -> protobuf chat events
- Done when: each mapped legacy message/command has at least one automated test.

## Chunk 2: End-to-End Integration Harness
- Add e2e tests that run Client + GameServer + ChatServer locally in protobuf mode.
- Include scripted checks for:
  - login success/failure
  - heartbeat timeout/reconnect
  - representative game actions from each command group
  - representative chat flows (join/send/whisper/relation)
- Done when: CI can execute e2e smoke suite in under an agreed threshold.

## Chunk 3: Dual-Stack Shadow Mode
- Run protobuf transport in parallel with legacy transport in non-prod/local test modes.
- Compare:
  - command counts
  - update counts
  - error/disconnect rates
- Done when: parity metrics remain within agreed tolerance.

## Chunk 4: Progressive Cutover
- Gate by flags:
  - internal/dev users first
  - then all local/default
  - then disable legacy path
- Define rollback switch to legacy transport.
- Done when: one-command rollback exists and is tested.

## Chunk 5: Security Validation
- Validate JWT enforcement:
  - no unauthenticated game/chat access
  - invalid/expired tokens rejected
  - audience/issuer checks enforced
- Add security logs for failed auth events.
- Done when: auth bypass attempts fail in automated tests.

## Chunk 6: Cleanup and Deletion
- Remove obsolete transport classes/usages:
  - `Reporter`-based networking path from client runtime
  - assignment transport wrappers used only for networking
  - legacy transport config once stable
- Keep adapters only where needed for offline tools/tests.
- Done when: protobuf path is the only production network protocol.
