# Current Client/Server Protocol Audit

## Scope
This audit covers the current wire communication between:
- `Client` (`View.View`)
- `GameServer` (`Server.HackerServer` + `Game.Computer`)
- `ChatServer` (`Server.ChatServer` + `util.UserHandler`)

Primary sources reviewed:
- `src/Client/java/View/View.java`
- `src/GameServer/java/Server/HackerServer.java`
- `src/HackWars/java/Game/Computer.java`
- `src/ChatServer/java/Server/ChatServer.java`
- `src/ChatServer/java/util/UserHandler.java`
- `src/Client/java/chat/client/ChatController.java`
- `src/ChatServer/java/chat/messages/*.java`
- `src/HackWars/java/Assignments/*.java`
- `src/HackWars/java/com/plink/dolphinnet/*.java`

## Transport Summary (Today)
- Legacy DolphinNet transport based on Java object serialization over TCP sockets (`ObjectInputStream` / `ObjectOutputStream`).
- Two TCP sockets per service connection:
  - one inbound stream (server -> client assignments)
  - one outbound stream (client -> server assignments)
- Game ports:
  - client connects `in=10021`, `out=10020`
  - server `Editor(receive=10020, send=10021)`
- Chat ports:
  - client connects `in=10026`, `out=10025`
  - server `Editor(receive=10025, send=10026)`
- Wire payloads are `Assignment` objects; many are wrapped in `ZippedAssignment` (zip-compressed serialized assignment).

## Session/Auth Flow (Today)
1. Client opens two sockets to each server via `Reporter`.
2. Server assigns connection ID by sending an `Integer` assignment message.
3. Client sends `LoginAssignment(user, pass, ip)` to both servers.
4. Password is XOR-obfuscated with assignment hash (`HashSingleton` key), not JWT.
5. Game server returns:
   - `LoginSuccessAssignment(ip, encryptedIP, npc, publicKey)` or
   - `LoginFailedAssignment`.
6. Chat server does not send a dedicated login success payload; chat session is considered active once transport is up and login processed.
7. Both sides use periodic `PingAssignment` to keep connection alive.

## Game Protocol Surface

### Client -> GameServer assignment types
- `LoginAssignment`
- `PingAssignment`
- `RemoteFunctionCall` (string function + `Object[]` parameters)

### `RemoteFunctionCall` function catalog (all current names)
- `changedailypay`, `changenetwork`, `changewatchport`, `changewatchtype`
- `cluedata`, `compilefile`, `createfolder`
- `decompilefile`, `deletefile`, `deletefirewall`, `deletefolder`, `deletelogs`, `deletemulti`, `deletewatch`
- `deposit`, `dochallenge`
- `emptypettycash`, `exit`
- `facebookdeposit`, `facebooktransfer`, `facebookupdate`, `facebookwithdraw`
- `fetchports`, `fetchwatches`, `finalizecancelled`
- `get`
- `hacktendoActivate`, `hacktendoTarget`, `healport`
- `installapplication`, `installequipment`, `installfirewall`, `installwatch`
- `makebounty`, `malget`
- `peekcode`, `peeklogs`, `portonoff`, `put`
- `repairequipment`, `replaceapplication`
- `requestattack`, `requestcancelattack`, `requestdirectory`, `requestequipment`, `requestfile`, `requestgame`, `requestpage`, `requestpurchase`, `requestsave`, `requestscan`, `requestsecondarydirectory`, `requesttask`, `requesttrigger`, `requestwebpage`, `requestzombieattack`, `requestzombiecancelattack`
- `savefile`, `savepage`, `saveportnote`, `sellfile`, `sellfilemulti`
- `setdefaultport`, `setdummyport`, `setfiledescription`, `setfileprice`, `setftppassword`, `setpreferences`, `setwatchnote`, `setwatchobservedports`, `setwatchonoff`, `setwatchquantity`, `setwatchsearchfirewall`
- `submit`
- `transfer`
- `uninstallport`, `unlock`
- `vote`, `withdraw`

### GameServer -> Client assignment types
- `LoginSuccessAssignment`
- `LoginFailedAssignment`
- `PingAssignment`
- `PacketAssignment` (system/game state deltas and UI payloads)
- `DamageAssignment` (health/combat/xp/cpu updates)
- `HacktendoPacket` exists but sending is currently disabled/commented in server flow.

### Key Game payload models inside assignments
- `PacketAssignment` fields include:
  - economy/system (`bankMoney`, `pettyCash`, `commodities`, defaults, counts, preferences)
  - hardware/network (`PacketPort[]`, `PacketWatch[]`, `PacketNetwork`, CPU/HD/memory stats)
  - filesystem/web (`directory`, `secondaryDirectory`, `HackerFile`, `title/body`)
  - control flags (`requestPrimary`, `requestSecondary`, `requestHardware`, `allowedDir`)
  - UX payloads (`messages[]`, `choices[]`, `peakCode`, `logUpdate`, `captcha`)
- `DamageAssignment` fields include:
  - xp buckets, `cpuCost`
  - `healthUpdates[]`
  - `damage[]`

## Chat Protocol Surface

### Client -> ChatServer assignment types
- `LoginAssignment`
- `PingAssignment`
- `MessageInPacket(ArrayMessageIn)`

### `ArrayMessageIn` message types currently emitted by client
- `MsgInSubChannels`
- `MsgInRelationList`
- `MsgInChannelText`
- `MsgInChannelTextMe`
- `MsgInChannelJoin`
- `MsgInChannelCreate`
- `MsgInChannelLeave`
- `MsgInWhisper`
- `MsgInAddAdmin`
- `MsgInMute`
- `MsgInRelationAdd`
- `MsgInChannelKick`

### ChatServer -> Client assignment types
- `PingAssignment`
- `MessageOutPacket(ArrayMessageOut)`
- legacy forced-logout path queues an empty `LoginAssignment` payload

### `ArrayMessageOut` message types currently produced server-side
- `MsgOutChannelText`
- `MsgOutChannelTextMe`
- `MsgOutChannelJoin`
- `MsgOutChannelLeave`
- `MsgOutChannelAdd`
- `MsgOutChannelRemove`
- `MsgOutChannelKick`
- `MsgOutWhisper`
- `MsgOutSubChannels`
- `MsgOutRelationList`
- `MsgOutRelationAdd`
- `MsgOutError`
- additional defined but low/no-usage in current UI flow: `MsgOutChannelNoDelete`, `MsgOutRelationRemove`

## Critical Legacy Constraints To Preserve During Migration
- Game and chat currently depend on push-style server updates (not purely request/response).
- Semantics rely heavily on partial/delta updates in packet objects.
- Client behavior is tolerant of message order jitter but expects regular pings.
- Some legacy commands are still present for backward compatibility (`facebook*`, `sellfile`, etc.).

## Immediate Migration Implication
The protobuf redesign must cover:
1. Auth handshake before normal traffic (JWT required).
2. Full equivalence of Game command set and Packet/Damage updates.
3. Full equivalence of Chat message-in/message-out classes.
4. Incremental rollout path (dual stack or hard cut with feature flag) to avoid breaking existing login/session flows.
