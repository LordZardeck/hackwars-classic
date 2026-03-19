# Data Module (Kotlin, JPA + Hibernate)

This module is intentionally isolated and does not replace any existing SQL callsites yet.
It provides object/repository mappings for the current legacy MySQL queries.

## Runtime DB Source

Schema verification was done against local MySQL at:

- host: `127.0.0.1`
- user: `root`
- password: empty

Observed databases:

- `hackwars`
- `hackerforum`
- `hackwars_drupal`
- `alex_chat`

`hackwars.bought_items` is currently not present in local schema. Mapping is still provided because legacy code still queries it.

## Query-to-Repository Mapping

- `util/sql.checkLogin(...)`:
  - `AuthRepository.forumPasswordMatches(...)`
- `Computer.checkLogin(...)`:
  - `AuthRepository.drupalPasswordMatches(...)`
  - `AuthRepository.forumUserExists(...)`
  - `AuthRepository.findForumLoginSnapshotByName(...)`
  - `AuthRepository.updateForumLastLoggedInNow(...)`
- `CheckOutHandler.fetchProfile(...)` / `Computer.loadLocalLogin(...)`:
  - `UserSaveRepository.findStatsXmlByIp(...)`
- `CheckOutHandler` profile save insert/update:
  - `UserSaveRepository.upsertStatsXmlByIp(...)`
- `Computer.checkPingTime(...)`:
  - `AuthRepository.findDrupalUidByIp(...)`
  - `PlayStatisticsRepository.insertSession(...)`
- `Network.loadNetworks(...)`:
  - `NetworkRepository.findNetworks()`
  - `NetworkRepository.findAttachedNetworks(...)`
  - `NetworkRepository.findNpcsByType(...)`
- `HackerLinker.getVisitorIP(...)`:
  - `DomainRepository.findDomainByIp(...)`
- `DropTable`:
  - `DropTableRepository.findDropEntries(...)`
  - `DropTableRepository.findItemDataById(...)`
- `GiveItemsSingleton`:
  - `StorePurchaseRepository.findPendingPurchasesByIp(...)`
  - `StorePurchaseRepository.markAsGiven(...)`
- Chat server SQL classes:
  - `ChatRepository.*`
