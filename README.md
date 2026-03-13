# HackWars a History

The HackWars MMORPG began in 2005, as a university project of Benjamin Coe,
Cameron McGuinness, Christian Battista, and Alexander Morrison.

The unique game, which combines a real programming language, with MMO game
mechanics has a great success: thousands of players enjoyed playing it throughout
the years, and an amazing open-source community grew up around it.

## What's This?

This repo contains the original HackWars java code, updated and modernized. Instead of running on applets,
it now runs within a dedicated desktop app.

## Project Layout

- `src/chatServer/java`: dedicated chat server module sources.
- `src/hackerServer/java`: hacker server module sources.
- `src/client/java`: desktop client module sources.
- `src/tomcat/java`: Tomcat integration module sources.
- `src/hackwars/java`: shared HackWars code used by multiple modules.
- `src/main/resources`: runtime data files (images, DB zips, config, etc.).
- `src/main/webapp`: web application assets and `WEB-INF/web.xml`.
