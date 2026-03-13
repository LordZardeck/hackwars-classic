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

- `src/ChatServer/java`: dedicated chat server module sources.
- `src/GameServer/java`: game server module sources.
- `src/Client/java`: desktop client module sources.
- `src/Networking`: shared Protocol Buffers contracts and networking utilities.
- `src/Tomcat/java`: Tomcat integration module sources.
- `src/HackWars/java`: shared HackWars code used by multiple modules.
- `src/main/resources`: runtime data files (images, DB zips, config, etc.).
- `src/main/webapp`: web application assets and `WEB-INF/web.xml`.

## Networking Module

The `:Networking` module contains cross-language protobuf contracts under
`src/Networking/proto/hackwars/networking/v1/networking.proto`.

Generate Java classes and protobuf descriptors:

```bash
./gradlew :Networking:build
```

Generated outputs:

- Java protobuf classes: `src/Networking/build/generated/source/proto/main/java`
- Descriptor set: `src/Networking/build/descriptors/networking.protoset`
- Exported proto schemas: `src/Networking/build/exported-proto`

Any Java module can consume it with:

```groovy
implementation project(':Networking')
```

Non-Java services (for example Go) can use the same `.proto` files directly from
`src/Networking/proto` or the exported copies under `build/exported-proto`.
