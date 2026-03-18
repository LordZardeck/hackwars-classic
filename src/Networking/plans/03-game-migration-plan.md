# Plan 03: GameServer Migration (Legacy Assignments -> Protobuf)

## Goal
Move game communication from `Assignment`/`RemoteFunctionCall` to typed protobuf while preserving all current gameplay behavior.

## Chunk 1: Build Game Protobuf Session Endpoint
- Add game protobuf server endpoint (new port pair optional during dual stack; single socket preferred).
- Apply Plan 01 auth state machine and JWT gate.
- Route authenticated `SERVICE` frames into `core.v1.Request` parsing and then a new `GameProtoDispatcher`.
- Done when: authenticated client can send a typed ping command and receive typed ping response.

## Chunk 2: Implement Command Group A (Core Session + Navigation)
- Migrate commands:
  - `fetchports`, `fetchwatches`, `requestpage`, `changenetwork`
  - `setdefaultport`, `healport`, `portonoff`, `setdummyport`
  - `requestequipment`, `installequipment`, `repairequipment`
- Bridge each command to existing `ApplicationData` paths.
- Done when: existing UI actions in these areas work over protobuf path.

## Chunk 3: Implement Command Group B (Filesystem + Files)
- Migrate commands:
  - `requestdirectory`, `requestsecondarydirectory`, `requestfile`, `requestgame`
  - `savefile`, `compilefile`, `decompilefile`
  - `deletefile`, `deletemulti`, `createfolder`, `deletefolder`
  - `setfiledescription`, `setfileprice`
- Preserve current callback/update behavior (`requestPrimary/requestSecondary`, directory/file pushes).
- Done when: file browser, script editor, and install choosers run without legacy transport.

## Chunk 4: Implement Command Group C (Combat + Attack + Watch)
- Migrate commands:
  - `requestattack`, `requestcancelattack`
  - `requestzombieattack`, `requestzombiecancelattack`
  - `installwatch`, `setwatchonoff`, `setwatchquantity`, `setwatchnote`, `setwatchsearchfirewall`, `setwatchobservedports`, `changewatchport`, `changewatchtype`, `deletewatch`
  - `peekcode`, `peeklogs`, `changedailypay`, `emptypettycash`, `finalizecancelled`
- Done when: attack pane + watch manager workflows match legacy behavior.

## Chunk 5: Implement Command Group D (Economy/Web/Store)
- Migrate commands:
  - `withdraw`, `deposit`, `transfer`
  - `requestwebpage`, `submit`, `vote`, `exit`, `savepage`
  - `requestpurchase`, `sellfile`, `sellfilemulti`, `makebounty`
  - `unlock`, `installapplication`, `replaceapplication`, `uninstallport`, `installfirewall`, `setftppassword`
  - `cluedata`, `dochallenge`, `requestscan`, `requestsave`, `requesttask`, `requesttrigger`
  - compatibility-only: `facebookdeposit`, `facebookwithdraw`, `facebooktransfer`, `facebookupdate`
- Done when: browser/store/banking/challenge flows are parity-tested.

## Chunk 6: Map Outbound Updates
- Replace outbound assignment push with protobuf updates:
  - login success/failure
  - ping/heartbeat
  - packet delta (`PacketAssignment` equivalent)
  - damage delta (`DamageAssignment` equivalent)
- Include mappers for:
  - `PacketPort`, `PacketWatch`, `PacketNetwork`, `HackerFile`
- Done when: `View` no longer depends on `PacketAssignment`/`DamageAssignment` for protobuf transport path.

## Chunk 7: Client Integration (Game Path)
- Add game protobuf client in `View` and migrate:
  - login send path
  - command send path (`addFunctionCall`)
  - packet consumption path
- Use shared connection contract:
  - auth uses `AUTH` frame + `auth.v1.AuthRequest`
  - runtime traffic uses `SERVICE` frame + `core.v1.Request/Response` wrappers
- Keep legacy reporter path behind feature flag until cutover.
- Done when: game client can run full login->play loop over protobuf transport.

## Chunk 8: Remove Legacy Game Transport
- After rollout confidence, remove DolphinNet game path:
  - `Reporter` usage for game in `View`
  - game assignment wrappers for transport
- Done when: game module no longer requires legacy serialized assignment transport for production mode.
