# Plan 01: JWT + Protobuf Transport Foundation

## Goal
Establish a new socket transport baseline shared by GameServer and ChatServer:
- length-delimited protobuf frames
- mandatory JWT validation on connect
- explicit connection state machine
- no app payloads before auth success

## Chunk 1: Define Wire Framing + Envelope
- Implement fixed framing rule: `uint32_be length` + protobuf payload bytes.
- Introduce top-level envelope (`NetworkEnvelope`) with:
  - `message_id`
  - `sent_unix_ms`
  - `service` (`GAME`, `CHAT`)
  - `oneof payload`
- Add parser/encoder utilities in `src/Networking/java/...`.
- Done when: transport tests round-trip frames and reject malformed/oversized frames.

## Chunk 2: Add JWT Auth Handshake Messages
- Add protobuf messages:
  - `AuthInit { jwt, client_build, client_nonce }`
  - `AuthAccepted { session_id, user_id, expires_unix_ms }`
  - `AuthRejected { reason_code, reason_text }`
- Require first inbound message on a new socket to be `AuthInit`.
- Done when: server closes unauthenticated sockets on first invalid/missing auth message.

## Chunk 3: Implement JWT Validation Service
- Add `JwtValidator` abstraction with config for:
  - signing key / JWKS
  - issuer, audience, clock skew
- Validate: signature, expiration, not-before, issuer, audience, subject/user.
- Bind validated claims into connection session context.
- Done when: unit tests cover valid token, expired token, bad signature, wrong audience.

## Chunk 4: Connection State Machine + Timeouts
- Add explicit states: `CONNECTED -> AUTHENTICATED -> ACTIVE -> CLOSED`.
- Enforce auth timeout (example: 5s) for unauthenticated sockets.
- Add heartbeat/idle timeout policy equivalent to current ping behavior.
- Done when: integration tests confirm state transitions and timeout-driven disconnects.

## Chunk 5: Feature Flags + Side-by-Side Runtime
- Add runtime flags:
  - `networking.transport=legacy|protobuf|dual`
  - `networking.jwt.required=true|false`
- In `dual`, keep legacy DolphinNet alive while new transport runs on new ports.
- Done when: both transports can run concurrently without shared-state corruption.

## Chunk 6: Observability + Failure Visibility
- Add structured logs and counters:
  - auth success/failure by reason
  - frame decode failures
  - disconnect reason
  - per-service connection counts
- Done when: on-call can diagnose connect/auth failures without debug logging.
