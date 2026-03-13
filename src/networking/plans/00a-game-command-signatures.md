# Game Command Signatures (From `HackerServer` Dispatch)

Source: `src/GameServer/java/Server/HackerServer.java` (`RemoteFunctionCall` handling).

Format:
- `commandName(paramType...) -> ApplicationData(functionName)`

## Session / Core
- `fetchports(String encryptedIp) -> fetchports`
- `fetchwatches(String encryptedIp) -> fetchwatches`
- `requestpage(String encryptedIp) -> requestpage`
- `changenetwork(String encryptedIp, String network) -> changenetwork`
- `setdefaultport(String encryptedIp, int port, Integer type) -> setdefaultport`
- `healport(String encryptedIp, int port) -> heal`
- `portonoff(String encryptedIp, int port, Boolean on) -> portonoff`
- `setdummyport(String encryptedIp, int port, Boolean dummy) -> setdummyport`
- `setpreferences(String encryptedIp, HashMap preferences) -> setpreferences`

## Equipment / Installation
- `requestequipment(String encryptedIp) -> requestequipment`
- `installequipment(String encryptedIp, Integer position, String name) -> installequipment`
- `repairequipment(String encryptedIp, Integer position, String name) -> repairequipment`
- `installapplication(String encryptedIp, int port, String path, String name) -> installapplication`
- `replaceapplication(String encryptedIp, int port, String path, String name) -> replaceapplication`
- `uninstallport(String encryptedIp, int port) -> uninstallport`
- `installfirewall(String encryptedIp, int port, String path, String name) -> installfirewall`
- `setftppassword(String encryptedIp, String password) -> setftppassword`

## Watch Management
- `installwatch(String encryptedIp, String path, String name, int type, int port) -> installwatch`
- `setwatchobservedports(String encryptedIp, Integer watchId, Integer[] observedPorts) -> setwatchobservedports`
- `setwatchquantity(String encryptedIp, Integer watchId, Float quantity) -> setwatchquantity`
- `setwatchonoff(String encryptedIp, Integer watchId, Boolean state) -> setwatchonoff`
- `setwatchnote(String encryptedIp, Integer watchId, String note) -> setwatchnote`
- `setwatchsearchfirewall(String encryptedIp, Integer watchId, Integer searchFirewall) -> setwatchsearchfirewall`
- `deletewatch(String encryptedIp, Integer watchId) -> deletewatch`
- `deletefirewall(String encryptedIp, Integer portId) -> deletefirewall`
- `changewatchport(String encryptedIp, Integer watchId, Integer portId) -> changewatchport`
- `changewatchtype(String encryptedIp, Integer watchId, Integer portId) -> changewatchtype`

## Filesystem / Files
- `requestdirectory(String encryptedIp, String path) -> requestdirectory`
- `requestsecondarydirectory(String sourceIp, String path, String targetEncryptedIp, int port) -> requestsecondarydirectory`
- `requestfile(String encryptedIp, String path, String name) -> requestfile`
- `requestgame(String encryptedIp, String path, String name) -> requestgame`
- `savefile(String encryptedIp, String path, HackerFile file) -> savefile`
- `compilefile(String encryptedIp, String path, HackerFile file, Float price) -> compilefile`
- `decompilefile(String encryptedIp, String location, String fileName, Float compileCost) -> decompilefile`
- `deletefile(String encryptedIp, String path, String name) -> deletefile`
- `deletemulti(String encryptedIp, Object[] allFiles) -> deletemulti`
- `createfolder(String encryptedIp, String directory) -> createfolder`
- `deletefolder(String encryptedIp, String directory) -> deletefolder`
- `setfiledescription(String encryptedIp, String path, String name, String description) -> setfiledescription`
- `setfileprice(String encryptedIp, String path, String name, Float price) -> setfileprice`

## Web / Store / Economy
- `requestwebpage(String targetIp, String sourceIpOrExternal, HashMap parameters) -> requestwebpage`
- `submit(String targetIp, String sourceIpOrExternal, HashMap parameters) -> submit`
- `requestpurchase(String targetIp, String sourceEncryptedIp, String fileName, Integer quantity) -> requestpurchase`
- `sellfile(String encryptedIp, String location, String fileName, Float compileCost, Integer quantity) -> requestsellfile`
- `sellfilemulti(String encryptedIp, Object[] allFiles) -> sellfilemulti`
- `savepage(String encryptedIp, String title, String body) -> savepage`
- `vote(String targetIp, String sourceEncryptedIp) -> vote`
- `exit(String targetIp, String sourceEncryptedIp) -> exit`
- `withdraw(Float amount, String encryptedIp, int port) -> withdraw`
- `deposit(Float amount, String encryptedIp, int port) -> deposit`
- `transfer(Float amount, String encryptedIp, String targetIp, int port) -> transfer`
- `makebounty(String sourceEncryptedIp, Boolean anonymous, String target, Integer type, String fileName, String folder, Integer iterations, Float reward) -> makebounty`

## Combat / Attack / Challenge
- `requestattack(String targetIp, int targetPort, String sourceEncryptedIp, int sourcePort, Integer[] secondaryPorts, String[][] scripts, Object[] extraInfo, Integer windowHandle) -> requestattack`
- `requestcancelattack(String encryptedIp, int port) -> requestcancelattack`
- `requestzombieattack(String targetIp, int targetPort, String sourceIp, int sourcePort, Integer[] secondaryPorts, String[][] scripts, Object[] extraInfo, String parentEncryptedIp) -> requestzombieattack`
- `requestzombiecancelattack(String sourceIp, int port, String targetEncryptedIp) -> requestcancelattack`
- `emptypettycash(String encryptedIp, String targetIp, int targetPort, int windowHandle) -> emptyPettyCash`
- `finalizecancelled(String encryptedIp, String targetIp, int targetPort) -> finalizecancelled`
- `peekcode(String encryptedIp, String targetIp, int port) -> peekcode`
- `peeklogs(String encryptedIp, String targetIp, int port) -> peeklogs`
- `changedailypay(String sourceIp, int port, String change, String finalizeEncryptedIp, int attackPort) -> changedailypay`
- `dochallenge(String encryptedIp, String code, String challengeId) -> dochallenge`
- `requestscan(String encryptedIp, String targetIp) -> requestscan`
- `requestsave(String fileName, HashMap saveData, String targetIp) -> requestsave`
- `requesttask(String fileName, String questId, String taskName, String targetIp) -> requesttask`
- `requesttrigger(String watchNote, HashMap triggerData, String sourceIp, String targetIp) -> requesttriggernote`
- `cluedata(String encryptedIp, String data) -> cluedata`
- `saveportnote(String encryptedIp, int port, String note) -> saveportnote`
- `deletelogs(String encryptedIp) -> deletelogs`
- `unlock(String encryptedIp, String code) -> unlock`
- `put(String sourceIp, int port, String name, String fetchPath, String putPath, String targetEncryptedIp, String password, Integer quantity) -> put`
- `get(String sourceIp, int port, String name, String fetchPath, String putPath, String targetEncryptedIp, String password, Integer quantity) -> get`
- `malget(String sourceIp, int port, String name, String fetchPath, String putPath, String targetEncryptedIp, int attackPort) -> malget`

## Legacy Facebook Commands
- `facebookdeposit(String ip, Float amount, int defaultPort) -> deposit`
- `facebookwithdraw(String ip, Float amount, int defaultPort) -> withdraw`
- `facebooktransfer(String ip, String ip2, Float amount, int defaultPort) -> transfer`
- `facebookupdate(String ip) -> facebookupdate`

## Hacktendo Commands
- `hacktendoActivate(int activateId, int activateType, String ip) -> hacktendoActivate`
- `hacktendoTarget(int targetX, int targetY, String ip, int currentX, int currentY) -> hacktendoTarget`
