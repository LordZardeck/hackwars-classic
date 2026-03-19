# Testing

This repo now has three main testing layers:

- fast module tests for unit- and component-level coverage
- offline protocol integration tests for game/chat service behavior
- client UI workflow tests for the desktop application

The goal is to make it possible to validate changes without manually starting the whole stack and clicking around the game every time.

## Current Testing Strategy

### 1. Fast Tests

These are the normal module test suites that run quickly and do not need the full application stack.

They currently cover areas like:

- client login/auth flow seams
- game server command dispatch and legacy command slices
- computer session, persistence, packet building, and runtime services
- networking module tests

Use this layer for the fastest regression check while iterating.

### 2. Offline Integration Tests

These tests live in the `:Integration` module and start an offline, deterministic stack.

They exercise:

- offline login
- game protocol handshake
- chat protocol handshake
- ping/reconnect style flows
- representative RPC/data flows

These tests use test seams instead of real PlayFab so they can run locally and in automation without external credentials.

### 3. Client UI Workflow Tests

These tests also live in the `:Integration` module and launch the real desktop client against offline fixtures.

They currently cover:

- opening major top-level windows
- seeded data loading into selected windows
- representative workflows like banking actions
- tracking known broken client flows as expected failures

This layer is intentionally closer to how a player uses the client. It is slower and requires a graphics environment.

## Test Commands

Run all commands from the repository root.

### Fastest Useful Check

```bash
./gradlew testFast
```

This runs the fast verification layer across the main modules.

### Offline Integration Layer

```bash
./gradlew integrationTest
```

This runs the protocol/service integration tests in `:Integration`.

### Client UI Workflow Layer

```bash
./gradlew clientUiTest
```

This runs the desktop UI workflow tests in `:Integration`.

Important notes:

- This must run in a non-headless environment.
- The client windows will briefly appear on screen.
- Some workflows are tracked as expected failures and are reported separately.

### Full Recommended Regression Pass

```bash
./gradlew testFast integrationTest clientUiTest
```

This is the current best end-to-end validation command for agent work and larger changes.

## Expected Failures

The UI suite supports valid tests that are known to fail because the product is currently broken in those areas.

At the moment, `clientUiTest` intentionally tracks known expected failures for:

- Web Browser page rendering workflow
- Hacktendo Game Player native loading

These should stay visible in test output until the underlying bugs are fixed. They are not a reason to remove the tests.

## Where The Tests Live

### Fast Tests

- `src/Client/test`
- `src/GameServer/test`
- `src/Networking/test`

### Integration Module

- `src/Integration/java`
- `src/Integration/integrationTest`
- `src/Integration/clientUiTest`

## Helpful Targeted Commands

Run one integration test class:

```bash
./gradlew :Integration:integrationTest --tests 'com.hackwars.integration.OfflineStackProtocolIntegrationTest'
```

Run one UI test class:

```bash
./gradlew :Integration:clientUiTest --tests 'com.hackwars.integration.ui.ClientWindowWorkflowTest'
```

Run one module’s tests:

```bash
./gradlew :Client:test
./gradlew :GameServer:test
./gradlew :Networking:test
```

## Manual Testing

Manual testing is still useful, especially for areas not yet covered by automation.

If you need to run the app manually, start with:

- [LOCAL_RUN.md](/Users/lordzardeck/.codex/worktrees/306e/hackwars-classic-original/LOCAL_RUN.md)

The long-term direction is to keep moving important gameplay and client workflows out of manual-only verification and into the automated layers above.

## Notes For Contributors

- Prefer `testFast` for quick local iteration.
- Use `integrationTest` when changing login, protocol, or service behavior.
- Use `clientUiTest` when changing client workflows, window behavior, or UI data loading.
- If a UI workflow is valid but currently broken, keep the test and mark it as an expected failure rather than skipping it.
