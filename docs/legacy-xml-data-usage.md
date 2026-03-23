# Legacy XML Data Usage

This document captures the current legacy XML save-state behavior in HackWars Classic so future migration plans can replace it without losing data or breaking hidden dependencies.

This is an audit only.
It does not change runtime behavior, database schema, or fixture data.

## Source Of Truth

### Verified Storage Contract

- The canonical persisted save payload is stored in `hackwars.user.stats`.
- The physical database column is `MEDIUMBLOB`, mapped in code as a nullable `ByteArray`.
- The application treats that blob as UTF-8 text and expects it to contain a full XML save document beginning with `<?xml ... ?>` and a `<save>` root.

Primary evidence:

- `src/Data/java/com/hackwars/data/entity/hackwars/UserEntities.kt`
- `src/Data/java/com/hackwars/data/repository/UserSaveRepository.kt`
- `src/main/resources/db/db.zip`
- Live MySQL on `127.0.0.1:3306`

### Verified Live Database State

Read-only inspection of the live local database on `127.0.0.1:3306` found:

- `222` total rows in `hackwars.user`
- `222` rows with non-null `stats`
- `168` distinct IP values
- duplicate IP rows exist
- `6` rows have a blank `ip`

Important duplicate-row behavior:

- `UserSaveRepository.findStatsXmlByIp(ip)` and `upsertStatsXmlByIp(ip, xml)` both select rows with:
  - `WHERE u.ip = :ip ORDER BY u.num ASC`
  - `.setMaxResults(1)`
- Current repository behavior is therefore “first row by `num ASC` wins”.
- Any migration that assumes one row per IP will be wrong for real data.

Primary evidence:

- `src/Data/java/com/hackwars/data/repository/UserSaveRepository.kt`

### Verified Live XML Corpus Shape

All `222` populated live `stats` rows decode as `<save>` documents.

Across the live corpus:

- All `222` rows contain `<save>`, `<stats>`, `<ports>`, `<watches>`, `<files>`, and `<website>`.
- `215` rows contain `<preferences>`.
- `221` rows contain `votecount`, `maximumpettycash`, `dropTable`, `defaultshipping`, `commodity`, and `commodityrespawn`.
- `220` rows contain `dailyPayReduction`.
- `221` rows contain `webdesignxp`, `redirectingxp`, `repairxp`, and `website.myvotes`.

Historical drift also exists in live data:

- Row `num=10652` is structurally close to the current shape but is missing `<dailyPayReduction>` and `<preferences>`.
- Row `num=11360` is an older sparse format with missing canonical fields and extra legacy top-level tags:
  - missing `votecount`
  - missing `dailyPayReduction`
  - missing `maximumpettycash`
  - missing `dropTable`
  - missing `defaultshipping`
  - missing `commodity`
  - missing `commodityrespawn`
  - missing `webdesignxp`
  - missing `redirectingxp`
  - missing `repairxp`
  - contains `<low>`, `<medium>`, `<high>`, `<rare>`, `<holiday>`, `<clue>`, and `<cluelevel>`

This means the real database contains both:

- a newer canonical `<save>` structure that the current typed persistence code models
- older save variants that the current parser partially tolerates via defaults, but does not model completely

## Primary Save Pipeline

### Database Read And Write Boundary

The database boundary is centralized in the Data module.

`UserSaveEntity` maps `hackwars.user`:

- `ip`: `char(128)`
- `stats`: `@Lob ByteArray?`
- `num`: primary key / identity

Code path:

- `src/Data/java/com/hackwars/data/entity/hackwars/UserEntities.kt`
- `src/Data/java/com/hackwars/data/repository/UserSaveRepository.kt`
- `src/Data/java/com/hackwars/data/service/GameDataServices.kt`

`UserSaveRepository.findStatsXmlByIp(ip)`:

- queries `UserSaveEntity`
- orders by `num ASC`
- returns the first row only
- converts `stats` bytes to `String` with `StandardCharsets.UTF_8`

`UserSaveRepository.upsertStatsXmlByIp(ip, xml)`:

- re-runs the same first-row lookup
- converts XML string to UTF-8 bytes
- inserts a new row if none exists
- otherwise updates `existing.stats`

`GameProfileDataService` is the formal service seam used by GameServer:

- `findProfileXmlByIp(ip)`
- `upsertProfileXmlByIp(ip, xml)`

### Load Path

The current player/NPC load path is:

1. `ComputerSessionService.loadLocalSaveXml(ip, active)`
2. `ComputerLoadCoordinator.execute(computer)`
3. `XmlComputerPersistence.parse(xml)`
4. `LegacyComputerPersistenceSupport.restoreSnapshot(computer, snapshot)`

Key files:

- `src/GameServer/java/game/computer/session/ComputerSessionService.kt`
- `src/GameServer/java/game/Computer.kt`
- `src/GameServer/java/game/computer/persistence/XmlComputerPersistence.kt`
- `src/GameServer/java/game/computer/persistence/LegacyComputerPersistenceSupport.kt`

Important behavior:

- `ComputerSessionService` treats the payload as an opaque XML string loaded from the DB.
- `XmlComputerPersistence` parses the XML into a typed `ComputerSnapshot`.
- `LegacyComputerPersistenceSupport.restoreSnapshot()` maps that typed snapshot back into the legacy mutable `Computer` object graph.

### Save Path

The current save path is:

1. `Computer.outputXML()`
2. `LegacyComputerPersistenceSupport.captureSnapshot(computer)`
3. `LegacyComputerPersistenceSupport.outputXml(computer)`
4. `XmlComputerPersistence.serialize(snapshot)`
5. `CheckOutHandler.processWork(...)`
6. `GameProfileDataService.upsertProfileXmlByIp(ip, xml)`
7. `UserSaveRepository.upsertStatsXmlByIp(ip, xml)`

Key files:

- `src/GameServer/java/game/Computer.kt`
- `src/GameServer/java/game/computer/persistence/LegacyComputerPersistenceSupport.kt`
- `src/GameServer/java/game/computer/persistence/XmlComputerPersistence.kt`
- `src/GameServer/java/game/CheckOutHandler.kt`
- `src/Data/java/com/hackwars/data/service/GameDataServices.kt`
- `src/Data/java/com/hackwars/data/repository/UserSaveRepository.kt`

Important behavior:

- The XML written back to the database is generated from a typed snapshot.
- Fields not represented in `ComputerSnapshot` cannot survive a parse-and-reserialize cycle.

## Canonical Save Structure

### Canonical Reference Sources

The best repo-local schema specimen for the intended modern save layout is:

- `src/GameServer/test/game/computer/persistence/XmlComputerPersistenceTest.kt`

`sampleSaveXml()` demonstrates the expected canonical `<save>` structure, including:

- scalar fields:
  - `ip`
  - `name`
  - `cputype`
  - `memorytype`
  - `password`
  - `hackcount`
  - `votecount`
  - `playertype`
  - `network`
  - `dailypaysize`
  - `dailyPayReduction`
  - `respawnmoney`
  - `maximumpettycash`
  - `dropTable`
  - `hdtype`
  - `lastpaid`
  - `pettycash`
  - `bank`
  - `defaultattack`
  - `defaultbank`
  - `defaultftp`
  - `defaulthttp`
  - `defaultshipping`
- repeated structures:
  - `currentquest`
  - `task`
  - `involvedquest`
  - `completedquest`
  - `allowedNetwork`
  - `logentry`
  - `global`
- nested sections:
  - `stats`
  - `commodity`
  - `commodityrespawn`
  - `ports`
  - `watches`
  - `files`
  - `website`
  - `equipment`
  - `preferences`

The typed schema that currently models that shape lives in:

- `src/GameServer/java/game/computer/persistence/ComputerSnapshot.kt`

### Current Parser Defaults

`XmlComputerPersistence` is tolerant of missing fields by design.

Examples:

- missing integers default to `0`
- missing floats default to `0f`
- missing `dailyPayReduction` defaults to `1f`
- missing `commodity` and `commodityrespawn` default to five `0f` values
- missing `preferences` defaults to an empty map
- missing `stats` defaults to a zeroed `ComputerStatsSnapshot`
- missing `ports`, `watches`, `files`, and `equipment` default to empty XML sections

Key file:

- `src/GameServer/java/game/computer/persistence/XmlComputerPersistence.kt`
- `src/GameServer/java/game/computer/persistence/ComputerSnapshot.kt`

This is why older live rows still parse successfully.

### Historical Drift In Real Data

The live database proves the save corpus is not fully normalized to the canonical shape.

#### Outlier `num=10652`

Observed characteristics:

- valid `<save>` document
- contains canonical sections including `stats`, `ports`, `watches`, `files`, `website`
- missing `<dailyPayReduction>`
- missing `<preferences>`

Risk:

- migration code must not assume those sections are always present in persisted XML

#### Outlier `num=11360`

Observed characteristics:

- valid `<save>` document
- sparse older structure
- still includes `ports`, `watches`, `files`, and `website`
- missing multiple canonical fields that the current parser now defaults
- contains legacy top-level tags not represented in `ComputerSnapshot`:
  - `low`
  - `medium`
  - `high`
  - `rare`
  - `holiday`
  - `clue`
  - `cluelevel`
- contains only a partial `<stats>` block

Risk:

- `XmlComputerPersistence.parse()` can read the row because of defaults
- `XmlComputerPersistence.serialize()` has no place to preserve those legacy top-level tags
- any future migration that round-trips through the typed snapshot model can silently erase data that still exists in historical rows

## Unstructured Parsing And Serialization

### `LegacyComputerPersistenceSupport`

This class is the largest concentration of XML-era unstructured data handling.

Key file:

- `src/GameServer/java/game/computer/persistence/LegacyComputerPersistenceSupport.kt`

Unstructured capture patterns:

- `computer.CurrentQuests` is iterated as raw `Map.Entry<*, *>`
- quest payloads are cast from `Array<*>`
- task payloads are cast from `Array<*>`
- completed quests are cast from `Array<*>`
- log entries are cast from `Array<*>`
- globals are read from a loose `ArrayList<Any?>`
- preferences are read from `Map.Entry<*, *>`

Unstructured restore patterns:

- quest/task state is rebuilt into `HashMap<String, Array<Any>>`
- completed quests are rebuilt as `arrayOf(id, label)`
- log messages are rebuilt as `arrayOf(message, ip)`
- preferences are copied into `HashMap<Any?, Any?>`
- `Computer.Stats` is reconstructed as `HashMap<Any?, Any?>`

XML-specific loose parsing:

- filesystem, equipment, ports, and watches are all loaded through `util.LoadXML`
- helper `text(node, loadXml)` reads `#text` nodes directly
- port/firewall parsing contains compatibility fallbacks for two firewall shapes
- program scripts are rebuilt by iterating `program.getTypeKeys()` and looking up tags dynamically

### `HackerFile.outputXML()` And `loadFile()`

Files are serialized and deserialized through stringly-typed XML content maps.

Key files:

- `src/HackWars/java/game/HackerFile.java`
- `src/GameServer/java/game/computer/persistence/LegacyComputerPersistenceSupport.kt`

Relevant behavior:

- `HackerFile.outputXML()` emits a `<file>` with `<content>`.
- It iterates over `getTypeKeys()` and writes arbitrary content entries as XML child tags.
- `specialAttribute1` and `specialAttribute2` are serialized as nested object-like maps with:
  - `name`
  - `long_desc`
  - `short_desc`
  - `value`
- `LegacyComputerPersistenceSupport.loadFile()` reverses this by:
  - looking up keys dynamically from `file.getTypeKeys()`
  - filling a `HashMap<String, Any>`
  - storing plain strings for most keys
  - storing nested `HashMap<String, String>` for `specialAttribute1/2`

This is effectively schema-by-convention, not strongly typed persistence.

### Program Script Serialization

Programs are persisted by an abstract dynamic contract:

- `Program.installScript(script: HashMap<*, *>)`
- `Program.getTypeKeys(): Array<String?>`
- `Program.outputXML(): String`

Key file:

- `src/GameServer/java/com/hackwars/game/program/Program.kt`

Representative subclasses:

- `AttackProgram`
- `Banking`
- `FTPProgram`
- `HTTPProgram`
- `ShippingProgram`
- `WatchProgram`

Behavior:

- `LegacyComputerPersistenceSupport.loadPorts()` and `loadWatches()` create a `HashMap<String, String>`
- they fill it by iterating `getTypeKeys()`
- they pass that dynamic map into `installScript(...)`
- each program serializes itself back into XML with custom tag names

Examples:

- attack/shipping use `initialize`, `continue`, `finalize`
- banking uses `deposit`, `withdraw`, `transfer`
- ftp uses `put`, `get`
- http uses `enter`, `exit`, `submit`
- watch uses `fire`

Risk:

- script payload shape is encoded partly in XML tags, partly in the subclass implementation, and partly in `getTypeKeys()`
- there is no shared typed schema for these script sections

### Search Bootstrap Raw Coercion

Search bootstrap is another important unstructured XML consumer.

Key files:

- `src/Data/java/com/hackwars/data/repository/SearchBootstrapRepository.kt`
- `src/GameServer/java/hackersearch/util/SearchHandler.kt`

Unstructured behavior:

- native query result is cast to `List<Array<Any?>>`
- `row[0]` is coerced into XML via `ByteArray`, `String`, or `toString()`
- `row[2]` is cast as `Number`
- `SearchHandler.loadBootstrapPage()` recursively finds `ip`, `title`, and `body` anywhere in the XML document
- extracted website body is then sanitized with regexes

This path is loosely coupled to the save XML blob, not to a typed website model.

## Secondary Consumers And Migration Dependencies

### Search Bootstrap From `user.stats`

The search index is populated by mining website data out of save XML.

Path:

- `SearchBootstrapRepository.findBootstrapRows()`
- `SearchHandler.bootstrapFromDatabase()`
- `SearchHandler.loadBootstrapPage(statsXml)`

Key files:

- `src/Data/java/com/hackwars/data/repository/SearchBootstrapRepository.kt`
- `src/GameServer/java/hackersearch/util/SearchHandler.kt`

What it depends on:

- `<ip>`
- `<title>`
- `<body>`

Migration implication:

- if website data moves out of the save blob, search bootstrap needs a replacement data source
- otherwise website indexing will silently disappear

### Integration Seed And Override XML

Integration tests currently synthesize a full save XML document.

Key files:

- `src/Integration/java/com/hackwars/integration/SeedScenario.java`
- `src/Integration/java/com/hackwars/integration/HackWarsStack.java`

Behavior:

- constructs a typed `ComputerSnapshot`
- serializes it with `XmlComputerPersistence`
- injects the raw XML as a local save override

Migration implication:

- test seeding still depends on XML as an interchange contract even though it starts from typed data

### VB Save File Editor

The old Visual Basic save editor is a separate direct consumer of `hackwars.user.stats`.

Key files:

- `HWJJStuff/HW Save File Editor/HW Save File Editor/Forms/Form1.vb`
- `HWJJStuff/HW Save File Editor/HW Save File Editor/Forms/frmScriptEditor.vb`

Behavior:

- reads `user.stats` directly from MySQL
- decodes bytes as UTF-8
- loads the XML into `XmlDocument`
- expects exactly one `<files>` node
- reconstructs directories by splitting `location`
- reads per-file fields with direct `Item(...)` lookups
- stores raw `<content>` inner XML
- reparses file content later based on file type

Migration implication:

- this tool will break if the DB blob is removed or if file content stops being XML-shaped
- it should be explicitly deprecated, replaced, or adapted in any real migration plan

### Tests That Encode Save Shape

Tests also act as save-format dependencies.

Key files:

- `src/GameServer/test/game/computer/persistence/XmlComputerPersistenceTest.kt`
- `src/GameServer/test/game/computer/session/ComputerSessionServiceTest.kt`
- `src/GameServer/test/game/CheckOutHandlerTest.kt`
- `src/GameServer/test/hackersearch/util/SearchHandlerTest.kt`

These tests confirm:

- save XML is still loaded as a raw string at the session boundary
- save XML is still persisted as a raw string at the checkout boundary
- search bootstrap still consumes website fields from save XML
- `sampleSaveXml()` remains the clearest in-repo canonical schema specimen

## Nearby XML Usage That Is Not `user.stats`

These areas use XML but are not part of the `hackwars.user.stats` save-state migration target.

### Drop Table Item Definitions

- `src/GameServer/java/game/DropTable.kt`

This parses XML `<file>` definitions for world/drop content, not player save rows.
It is still legacy and untyped, but it is not `user.stats`.

### RSS / News Feed XML

- `src/HackWars/java/com/plink/dolphinstem/RssSource.java`
- `src/HackWars/java/com/plink/dolphinstem/ReutersSource.java`

These parse RSS/news XML and are unrelated to save-state persistence.

### Minigame / Client XML

- `src/Client/java/game/mmo/MMOEngine.java`
- `src/Client/java/hacktendo/OpenGLViewport.java`

These load separate XML assets for minigame content, not account saves.

## Migration Cautions

- Duplicate IP rows are real and current repository semantics preserve only the earliest `num` row.
- Search bootstrap depends on website fields embedded inside save XML.
- Historical save rows contain fields not modeled by `ComputerSnapshot`.
- Historical save rows also omit fields that the modern parser now defaults.
- A migration that only models the modern typed snapshot can silently erase old-format fields during transform or backfill.
- The VB save editor is an external direct consumer of the DB blob and should be treated as a separate compatibility decision.

## Reproducible Evidence

### Read-Only SQL Used For Verification

Schema and row counts:

```sql
DESCRIBE user;

SELECT COUNT(*) AS total_rows,
       SUM(stats IS NOT NULL) AS rows_with_stats,
       SUM(stats IS NULL) AS rows_without_stats
FROM user;

SELECT COUNT(DISTINCT ip) AS distinct_ips,
       SUM(ip = '') AS blank_ip_rows
FROM user;

SELECT ip, COUNT(*) AS c
FROM user
GROUP BY ip
HAVING COUNT(*) > 1
ORDER BY c DESC, ip ASC
LIMIT 20;
```

Preview real save blobs:

```sql
SELECT ip,
       OCTET_LENGTH(stats) AS bytes,
       LEFT(CONVERT(stats USING utf8mb4), 240) AS preview
FROM user
WHERE stats IS NOT NULL
ORDER BY num ASC
LIMIT 5;
```

Corpus-wide tag presence checks:

```sql
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<save>%';
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<stats>%';
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<ports>%';
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<watches>%';
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<files>%';
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<website>%';
SELECT COUNT(*) FROM user WHERE stats IS NOT NULL AND CONVERT(stats USING utf8mb4) LIKE '%<preferences>%';
```

Historical drift checks:

```sql
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<low>%';
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<medium>%';
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<high>%';
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<rare>%';
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<holiday>%';
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<clue>%';
SELECT COUNT(*) FROM user WHERE CONVERT(stats USING utf8mb4) LIKE '%<cluelevel>%';
```

Outlier rows preserved for future migration discussion:

```sql
SELECT num, ip, LEFT(CONVERT(stats USING utf8mb4), 2500)
FROM user
WHERE num IN (10652, 11360);
```

### Bundled Dump Evidence

Offline structural corroboration is also available from:

- `src/main/resources/db/db.zip`

Relevant files inside the archive:

- `hackwars.sql`
- `hackwars_data.sql`
- `hackwars_schema.sql`

The dump confirms:

- `hackwars.user.stats` is stored as `MEDIUMBLOB`
- inserted `user.stats` payloads are full XML save documents

### In-Repo Canonical Schema Specimen

The clearest intended schema specimen remains:

- `src/GameServer/test/game/computer/persistence/XmlComputerPersistenceTest.kt`

Use that file together with the live DB outliers above when designing any migration or backfill.
