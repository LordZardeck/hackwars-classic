# Plan 01: JWT + Protobuf Transport Foundation

## Goal
Establish a new socket transport baseline shared by GameServer and ChatServer:
- length-delimited protobuf frames
- mandatory JWT validation on connect
- explicit connection state machine
- no app payloads before auth success

## Chunk 1: Define Wire Framing + Message Type Header
- Implement fixed framing rule: `int8 message_type` + `int32_be payload_length` + protobuf payload bytes.
- Define wire message types:
  - `AUTH` (first frame on every new connection)
  - `SERVICE` (normal game/chat traffic after auth)
- Enforce payload caps by message type:
  - `AUTH` max payload size = `2048` bytes
  - `SERVICE` max payload size = configurable runtime limit
- Add parser/encoder utilities in `src/Networking/java/...`.
- Done when: transport tests round-trip typed frames and reject malformed, unsupported-type, and oversized frames.

## Chunk 2: Add JWT Auth Handshake Messages (Shared)
- Add shared protobuf auth messages in `auth/v1/auth.proto`:
  - `AuthRequest { jwt(bytes), client_build }`
  - `AuthResponse { oneof { AuthAccepted, AuthRejected } }`
  - `AuthAccepted { success }`
  - `AuthRejected { reason_code, reason_text }`
- Keep service payload schemas in per-service `.proto` files (game/chat are separate).
- Require first inbound frame on a new socket to be message type `AUTH`, and its payload to parse as `AuthRequest`.
- Done when: server closes unauthenticated sockets on first invalid/missing auth frame or oversized auth payload.

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
