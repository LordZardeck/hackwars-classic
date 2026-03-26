# Rewrite Testing Plan

## Scope
- This plan defines the retained rewrite verification stack: unit, integration, migration, UI workflow, MVC guardrail, screenshot parity, deterministic dev-mode, and full retained integration audit.
- Screenshot baselines, MVC guardrails, and deterministic UI harness rules are immediate prerequisites, not optional follow-up work.

## Locked Verification Rules
- No retained milestone or retained client family is `done` without green task-specific verification, controller tests, UI workflow tests, screenshot parity tests, and MVC guardrail coverage where applicable.
- Screenshot parity must run under fixed look and feel, fixed font stack, fixed DPI, fixed window sizes, and deterministic fixture data.
- UI artifact capture must persist both the rendered screenshot and enough metadata to reproduce the run: look and feel, scale, JVM, OS, and seeded fixture identity.
- MVC guardrails must fail when a retained view class imports controllers, protocol contracts, stores, coroutine APIs, or registers listeners in constructors or initialization blocks.

## Deterministic UI Harness Rules
- Look and feel: rewrite-owned HackWars cross-platform look and feel only
- Font stack: pinned deterministic stack shipped with retained rewrite resources
- DPI and scale: fixed for all screenshot jobs and stored with artifacts
- Window sizes: pinned per retained family and stored in the baseline manifest
- Artifact capture: source screenshot, diff screenshot, metadata manifest, and failing test name on every parity miss
- Seed data: deterministic dev-mode fixture with valid RFC 5737 IPv4 addresses only

## Execution Lanes
| Lane | Scope | Primary write scope |
| --- | --- | --- |
| `tooling` | common test stack, artifact capture, ui harness | `build.gradle*`, `gradle/**`, `src/**/test/**`, `src/**/uiTest/**` |
| `integration` | persistence, server, chat, and dev-harness integration verification | `src/**/integrationTest/**`, `src/**/test/**`, `src/RewriteTestKit/**` |
| `ui_parity` | screenshot baselines and parity packs by pass | `src/RewriteClient/src/uiTest/**`, `src/RewriteClientDev/**`, `artifacts/**` |
| `audits` | MVC guardrails, parity evidence, and final audit rules | `src/**/test/**`, `plans/rewrite/**` |

## Task Cards
### RW-TEST-001 - Standardize rewrite test stack
- Status: `done`
- Priority: `P0`
- Execution lane: `tooling`
- Worker role: `worker`
- Depends on: `RW-M1-001`
- Ready when: retained rewrite modules exist
- Parallel with: `RW-TEST-002`
- Allowed write scope: `build.gradle*`, `gradle/**`, `src/**/test/**`
- Autonomous next: `RW-TEST-002`
- Fallback if blocked: `none`
- Verification scope: `./gradlew rewriteUnitTest`

### RW-TEST-002 - Artifact capture conventions and failure triage output
- Status: `in_progress`
- Priority: `P1`
- Execution lane: `tooling`
- Worker role: `worker`
- Depends on: `RW-TEST-001`
- Ready when: retained test stack is standardized
- Parallel with: `RW-TEST-003`, `RW-TEST-004`
- Allowed write scope: `src/**/test/**`, `src/**/uiTest/**`, `artifacts/**`
- Autonomous next: `RW-TEST-004`
- Fallback if blocked: `RW-TEST-003`
- Verification scope: `./gradlew rewriteCheck`

### RW-TEST-003 - Testcontainers-backed rewrite integration harness
- Status: `in_progress`
- Priority: `P1`
- Execution lane: `integration`
- Worker role: `worker`
- Depends on: `RW-DATA-001`
- Ready when: retained PostgreSQL runtime exists
- Parallel with: `RW-TEST-002`, `RW-DATA-005A`
- Allowed write scope: `src/**/integrationTest/**`, `src/RewriteTestKit/**`
- Autonomous next: `RW-TEST-012`
- Fallback if blocked: `RW-DATA-005A`
- Verification scope: `./gradlew rewriteIntegrationTest rewriteMigrationTest`

### RW-TEST-004 - Deterministic UI harness and screenshot baseline system
- Status: `in_progress`
- Priority: `P0`
- Execution lane: `ui_parity`
- Worker role: `worker`
- Depends on: `RW-TEST-002`, `RW-CLIENT-X2`
- Ready when: artifact capture conventions and rewrite look and feel are in place
- Parallel with: `RW-TEST-005`, `RW-TEST-006`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `src/RewriteClientDev/**`, `artifacts/**`
- Autonomous next: `RW-CLIENT-X4`
- Fallback if blocked: `RW-TEST-006`
- Verification scope: `./gradlew rewriteUiTest`

### RW-TEST-005 - MVC architecture guardrail tests
- Status: `ready`
- Priority: `P0`
- Execution lane: `audits`
- Worker role: `worker`
- Depends on: `RW-CLIENT-X1`
- Ready when: strict MVC foundation work defines the retained package boundaries
- Parallel with: `RW-CLIENT-X2`, `RW-TEST-006`
- Allowed write scope: `src/RewriteClient/src/test/**`, `src/RewriteClientModel/src/test/**`
- Autonomous next: `RW-TEST-007`
- Fallback if blocked: `RW-CLIENT-X1`
- Verification scope: `./gradlew :RewriteClient:test`

### RW-TEST-006 - Legacy screenshot baseline capture workflow
- Status: `todo`
- Priority: `P0`
- Execution lane: `ui_parity`
- Worker role: `worker`
- Depends on: `RW-TEST-004`
- Ready when: deterministic UI harness can capture and store retained baseline artifacts
- Parallel with: `RW-TEST-005`, `RW-CLIENT-X4`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `artifacts/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `RW-TEST-007`
- Fallback if blocked: `RW-TEST-004`
- Verification scope: `./gradlew :RewriteClient:uiTest`

### RW-TEST-007 - Pass 2 login and shell parity verification pack
- Status: `todo`
- Priority: `P1`
- Execution lane: `ui_parity`
- Worker role: `verifier`
- Depends on: `RW-TEST-005`, `RW-TEST-006`, `RW-CLIENT-C0C`, `RW-CLIENT-C1C`
- Ready when: login and shell screenshot closure tasks are ready for approval
- Parallel with: `RW-TEST-008`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `src/RewriteClientDev/**`, `artifacts/**`
- Autonomous next: `RW-TEST-008`
- Fallback if blocked: `RW-CLIENT-C1C`
- Verification scope: `./gradlew :RewriteClient:uiTest :RewriteClientDev:test`

### RW-TEST-008 - Pass 3 network and systems parity verification pack
- Status: `todo`
- Priority: `P1`
- Execution lane: `ui_parity`
- Worker role: `verifier`
- Depends on: `RW-TEST-005`, `RW-TEST-006`, `RW-CLIENT-C6C`, `RW-CLIENT-C7C`, `RW-CLIENT-C8C`
- Ready when: retained network, port management, and inventory screenshot closure tasks are ready for approval
- Parallel with: `RW-TEST-009`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `artifacts/**`
- Autonomous next: `RW-TEST-009`
- Fallback if blocked: `RW-CLIENT-C6C`
- Verification scope: `./gradlew :RewriteClient:uiTest`

### RW-TEST-009 - Pass 4 economy, files, FTP, browser, and editor parity verification pack
- Status: `todo`
- Priority: `P1`
- Execution lane: `ui_parity`
- Worker role: `verifier`
- Depends on: `RW-TEST-005`, `RW-TEST-006`, `RW-CLIENT-C2C`, `RW-CLIENT-C3C`, `RW-CLIENT-C4C`, `RW-CLIENT-C5C`
- Ready when: retained economy, files, FTP, and browser screenshot closure tasks are ready for approval
- Parallel with: `RW-TEST-010`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `artifacts/**`
- Autonomous next: `RW-TEST-010`
- Fallback if blocked: `RW-CLIENT-C3C`
- Verification scope: `./gradlew :RewriteClient:uiTest`

### RW-TEST-010 - Pass 5 combat runtime parity verification pack
- Status: `todo`
- Priority: `P1`
- Execution lane: `ui_parity`
- Worker role: `verifier`
- Depends on: `RW-TEST-005`, `RW-TEST-006`, `RW-CLIENT-C9C`
- Ready when: retained combat screenshot closure tasks are ready for approval
- Parallel with: `RW-TEST-011`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `artifacts/**`
- Autonomous next: `RW-TEST-011`
- Fallback if blocked: `RW-CLIENT-C9C`
- Verification scope: `./gradlew :RewriteClient:uiTest`

### RW-TEST-011 - Pass 6 utilities, help, tutorial, and chat parity verification pack
- Status: `todo`
- Priority: `P2`
- Execution lane: `ui_parity`
- Worker role: `verifier`
- Depends on: `RW-TEST-005`, `RW-TEST-006`, `RW-CLIENT-C10C`, `RW-CLIENT-C11C`
- Ready when: retained utility and client-chat screenshot closure tasks are ready for approval
- Parallel with: `RW-CHAT-005B`
- Allowed write scope: `src/RewriteClient/src/uiTest/**`, `artifacts/**`
- Autonomous next: `RW-TEST-012`
- Fallback if blocked: `RW-CLIENT-C10C`
- Verification scope: `./gradlew :RewriteClient:uiTest :RewriteClientDev:test`

### RW-TEST-012 - Pass 7 retained full integration and parity audit
- Status: `todo`
- Priority: `P0`
- Execution lane: `audits`
- Worker role: `verifier`
- Depends on: `RW-TEST-003`, `RW-TEST-007`, `RW-TEST-008`, `RW-TEST-009`, `RW-TEST-010`, `RW-TEST-011`
- Ready when: all retained pass-level verification packs are green
- Parallel with: `none`
- Allowed write scope: `src/**/test/**`, `src/**/uiTest/**`, `artifacts/**`, `plans/rewrite/feature-inventory/PLAN.md`
- Autonomous next: `none`
- Fallback if blocked: `RW-TEST-011`
- Verification scope: `./gradlew rewriteCheck rewriteIntegrationTest rewriteMigrationTest rewriteUiTest`

## Global Acceptance Gates
- No retained client task may be marked `done` without a named testing-pack dependency from this file.
- No retained parity pack may be marked `done` without approved baseline artifacts and diff artifacts on failure.
