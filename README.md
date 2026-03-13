# HackWars a History

The HackWars MMORPG began in 2005, as a university project of Benjamin Coe,
Cameron McGuinness, Christian Battista, and Alexander Morrison.

The unique game, which combines a real programming language, with MMO game
mechanics has a great success: thousands of players enjoyed playing it throughout
the years, and an amazing open-source community grew up around it.

Alas, it is now virtually impossible to run the old browser deployment in modern web-browsers
which brings us to a new stage in HackWars' history: let's rebuild an
open-source version in JavaScript.

## What's This?

This repo contains the crufty code for the original HackWars Java client,
warts intact.

## Project Layout

- `src/chatServer/java`: dedicated chat server module sources.
- `src/hackerServer/java`: hacker server module sources.
- `src/client/java`: desktop client module sources.
- `src/tomcat/java`: Tomcat integration module sources.
- `src/hackwars/java`: shared HackWars code used by multiple modules.
- `src/main/resources`: runtime data files (images, DB zips, config, etc.).
- `src/main/webapp`: web application assets and `WEB-INF/web.xml`.
- `settings.gradle`: Gradle multi-project wiring for the 5 modules.
- `src/*/*.gradle`: module-owned Gradle build files (`chatServer.gradle`, `hackerServer.gradle`, `client.gradle`, `tomcat.gradle`, `hackwars.gradle`).
- Module tasks are run with project-qualified names (for example, `:hackerServer:runHackerServer`, `:tomcat:war`).
- `libs/legacy`: legacy third-party jars bundled into build/runtime classpaths.
- `build/libs/hackwars.war`: deployable artifact for an external Tomcat server.
