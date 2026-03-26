# HackWars Classic Local Run

## 1. Rewrite Client Dev Mode

This is the recommended local investigation path for the rewrite desktop client. It does not require PlayFab or a live rewrite server, and it boots against a deterministic in-memory GAME harness with seeded data.

Run either command from the repository root:

```bash
./gradlew rewriteClientDev
```

or

```bash
./gradlew :RewriteClientDev:run
```

Use these locked credentials at the login screen:

- email: `localuser`
- password: `password1234`

Important notes:

- `:RewriteClient:run` is still the live-edge rewrite client path and is unchanged by dev mode.
- `:RewriteGameServer:run` is currently scaffold-only and is not a usable local gameplay backend.
- Dev mode is GAME-only in this slice; CHAT remains out of scope.

## 2. Live-Edge Rewrite Client

If you specifically need the live-edge rewrite client wiring rather than the deterministic harness, launch:

```bash
./gradlew :RewriteClient:run
```

This path still expects the current live service dependencies and is not the recommended manual investigation path.

## 3. Legacy Stack

The legacy client/server stack below still relies on MySQL 5.7 compatibility, not MySQL 8.

### Start Compatible MySQL (Docker)

```bash
open -a OrbStack
docker rm -f hackwars-mysql57 >/dev/null 2>&1 || true
docker run -d --platform linux/amd64 \
  --name hackwars-mysql57 \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes \
  -p 3306:3306 \
  mysql:5.7
```

Wait for startup:

```bash
docker logs -f hackwars-mysql57
```

Stop tailing when you see: `ready for connections`.

### Import Databases

```bash
mkdir -p /tmp/hwdb_20260312
unzip -qo src/main/resources/db/db.zip -d /tmp/hwdb_20260312
sed 's/ ROW_FORMAT=FIXED//g' /tmp/hwdb_20260312/hackwars.sql > /tmp/hwdb_20260312/hackwars_mysql57.sql

mysql -h 127.0.0.1 -P 3306 -u root -e \
  "DROP DATABASE IF EXISTS hackwars; DROP DATABASE IF EXISTS hackerforum; DROP DATABASE IF EXISTS alex_chat; DROP DATABASE IF EXISTS hackwars_drupal;"
mysql -h 127.0.0.1 -P 3306 -u root < /tmp/hwdb_20260312/hackwars_mysql57.sql
mysql -h 127.0.0.1 -P 3306 -u root < /tmp/hwdb_20260312/hackerforum.sql
mysql -h 127.0.0.1 -P 3306 -u root < /tmp/hwdb_20260312/chat.sql
mysql -h 127.0.0.1 -P 3306 -u root < /tmp/hwdb_20260312/hackwars_drupal.sql
```

### Run Socket Services (2 terminals)

Terminal A:

```bash
./gradlew :GameServer:runHackerServer
```

Terminal B:

```bash
./gradlew :ChatServer:runChatServer
```

### Launch Client

```bash
./gradlew :Client:runClientDesktop
```

### Build Native Client App (Current OS)

```bash
./gradlew :Client:packageClientNative
```

For macOS universal output, run the task with a universal macOS JDK so the generated app image is universal.
