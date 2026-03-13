# HackWars Classic Local Run

This repo contains a legacy MySQL JDBC driver that is compatible with MySQL 5.7, not MySQL 8.

## 1) Start Compatible MySQL (Docker)

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

## 2) Import Databases

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

## 3) Build Deployable WAR

```bash
./gradlew :tomcat:war
```

Output:

```text
build/libs/hackwars.war
```

## 4) Deploy WAR To Managed Tomcat (`build/tomcat`)

```bash
./gradlew :tomcat:deployWar
```

This installs Tomcat under `build/tomcat` if needed, then copies `build/libs/hackwars.war` into that Tomcat's `webapps/`.

Run managed Tomcat:

```bash
./gradlew :tomcat:runTomcat
```

Stop managed Tomcat:

```bash
./gradlew :tomcat:stopTomcat
```

## 5) Run Socket Services (2 terminals)

Terminal A:

```bash
./gradlew :hackerServer:runHackerServer
```

Terminal B:

```bash
./gradlew :chatServer:runChatServer
```

## 6) Launch Client

```bash
./gradlew :client:runClientDesktop
```

## 7) Build Native Client App (Current OS)

```bash
./gradlew :client:packageClientNative
```

For macOS universal output, run the task with a universal macOS JDK so the generated app image is universal.
