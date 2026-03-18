# Chat Message Signatures

Sources:
- `src/Client/java/chat/client/ChatController.java`
- `src/ChatServer/java/chat/messages/*.java`
- `src/ChatServer/java/chat/server/*.java`

## Transport Wrappers
- Client -> server: `MessageInPacket(ArrayMessageIn)`
- Server -> client: `MessageOutPacket(ArrayMessageOut)`
- Keepalive both directions: `PingAssignment`

## Client -> Chat (`MsgIn*`)
- `MsgInSubChannels(sender)`
- `MsgInRelationList(sender)`
- `MsgInChannelText(sender, message, channelName)`
- `MsgInChannelTextMe(sender, message, channelName)`
- `MsgInChannelJoin(sender, channelName, password)`
- `MsgInChannelCreate(sender, channelName, password)`
- `MsgInChannelLeave(sender, channelName)`
- `MsgInWhisper(sender, reciver, message)`
- `MsgInAddAdmin(sender, reciver)`
- `MsgInMute(sender, reciver)`
- `MsgInRelationAdd(sender, nameToAdd, comment, friend, ignore)`
- `MsgInChannelKick(sender, channelName, userToBeKicked)`
- Defined but not active in client send path: `MsgInChannelNoDelete(sender, channelName)`

## Server -> Chat Client (`MsgOut*`)
- `MsgOutChannelText(reciver, channelName, senderName, message)`
- `MsgOutChannelTextMe(reciver, channelName, senderName, message)`
- `MsgOutChannelJoin(reciver, channelName, userList[])`
- `MsgOutChannelLeave(reciver, channelName)`
- `MsgOutChannelAdd(reciver, channelName, userToAdd, admin)`
- `MsgOutChannelRemove(reciver, channelName, userToRemove)`
- `MsgOutChannelKick(reciver, channelName)`
- `MsgOutWhisper(reciver, sender, message)`
- `MsgOutSubChannels(reciver, subscribedChannels[], users[][])` + admin marker map
- `MsgOutRelationList(reciver, names[], comments[], friends[], ignore[], online[])`
- `MsgOutRelationAdd(reciver, name, comment, friend, ignore, online)`
- `MsgOutError(reciver, message)`
- Defined but low/no active client usage: `MsgOutChannelNoDelete`, `MsgOutRelationRemove`

## Server-Generated Chat Events
- Channel lifecycle events emitted by `ZUserChannel`:
  - join -> `MsgOutChannelJoin` + `MsgOutChannelAdd` fanout
  - leave/kick -> `MsgOutChannelLeave` + `MsgOutChannelRemove` or `MsgOutChannelKick`
- Message events emitted by `MsgProcessorThread`:
  - channel text -> `MsgOutChannelText` / `MsgOutChannelTextMe`
  - whisper -> `MsgOutWhisper`
  - sub-channel sync -> `MsgOutSubChannels`
  - relation sync -> `MsgOutRelationList` / `MsgOutRelationAdd`
  - errors -> `MsgOutError`
