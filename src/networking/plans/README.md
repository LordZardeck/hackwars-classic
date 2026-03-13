# Networking Migration Plan Index

Execute these plans in order:

1. `00-current-protocol-audit.md`
2. `00a-game-command-signatures.md`
3. `00b-chat-message-signatures.md`
4. `01-foundation-jwt-transport-plan.md`
5. `02-protobuf-schema-plan.md`
6. `03-game-migration-plan.md`
7. `04-chat-migration-plan.md`
8. `05-rollout-validation-plan.md`

Suggested execution mode for Codex agents:
- One chunk at a time.
- Merge and test after each chunk.
- Keep legacy transport behind flags until Plans 03 and 04 are complete.
