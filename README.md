# AutoBattle

Minecraft Java Edition 1.21.8 Fabric server-side multiplayer AI autobattler.

Players do not directly control their fighters. Each player teaches one robot with three natural-language Doctrine rules, observes an automated round, and may revise one Doctrine line between rounds.

## MVP

- 4-player FFA baseline
- One zombie robot per player
- Dyed leather chestplate for robot identity
- Melee-only combat
- Central CORE objective
- Five rounds
- One player command per round
- TypeSafe Jev chooses high-level tactical plans; Minecraft server code executes them
- Vanilla clients; no client-side mod required

## TypeSafe Jev

Production AutoBattle uses the TypeSafe System One API with the `jev-latest` model.

Configure the server process with:

```text
TYPESAFE_API_KEY=<your key>
```

Optional overrides:

```text
TYPESAFE_BASE_URL=https://api.typesafe.ai
TYPESAFE_DEFAULT_MODEL=jev-latest
```

The API key is read only from the server environment and must not be committed to the repository or sent to clients.

When `TYPESAFE_API_KEY` is absent, AutoBattle logs a warning and uses `ScriptedJevClient`. This keeps local development and offline gameplay testing available without changing the game pipeline.

Jev only selects from server-generated tactical candidates:

```text
ENGAGE_<COLOR>
CHASE_<COLOR>
CAPTURE_CORE
DEFEND_CORE
RETREAT
REPOSITION
```

Doctrine text is state data, not executable game logic. The server validates every returned plan before applying it.

## Native Dialog flow

Minecraft 1.21.8 native Dialogs are used for the player-facing training loop:

```text
All players Ready
    ↓
Doctrine Setup Dialog
    - Doctrine 1
    - Doctrine 2
    - Doctrine 3
    ↓
Countdown / Round
    ↓
Round Review Dialog
    ↓
Doctrine Edit Dialog
    - edit exactly one line
      or
    - keep current Doctrine
    ↓
Next Round
```

Free-form Doctrine text is submitted with Minecraft's custom dialog action payload rather than being interpolated into a command string. Spaces, quotes, Korean text and other normal input therefore remain data.

The existing `/autobattle doctrine ...` and `/autobattle review` commands remain available as debugging/fallback paths.

## Stack

- Minecraft 1.21.8
- Fabric Loader 0.18.0
- Fabric API 0.136.0+1.21.8
- Java 21
- Mojang official mappings
- TypeSafe System One / Jev

## Development

```powershell
.\gradlew.bat genSources
.\gradlew.bat build
.\gradlew.bat runServer
```

For production Jev testing, launch the server with `TYPESAFE_API_KEY` present in the server process environment.

## License

MIT
