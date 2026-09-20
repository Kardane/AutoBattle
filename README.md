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

## Configuration

On first server launch, AutoBattle creates:

```text
config/autobattle.yml
```

Edit this file and either restart the server or run:

```text
/autobattle admin reload
```

The reload command is intentionally restricted to an empty `LOBBY` so an active match cannot change rules underneath running robots. The YAML contains the TypeSafe API configuration and the main game tuning values:

- TypeSafe API key, base URL, and model
- minimum players, round count, round/countdown/respawn/command timing
- AI decision interval, lock, debounce, timeout, minimum confidence, and fallback retreat threshold
- Doctrine maximum line length
- robot HP, damage, movement/follow range, regeneration
- tactical movement speeds and leash distances
- kill/assist/CORE scoring and assist window
- CORE capture/hold timings
- dimension, CORE position/radius, robot/viewer spawns, reposition nodes

Example:

```yaml
typesafe:
  api-key: "ts_your_key_here"
  base-url: "https://api.typesafe.ai"
  model: "jev-latest"

match:
  minimum-players: 4
  rounds: 5
  round-duration-seconds: 90
  countdown-seconds: 5
  respawn-seconds: 7
  command-duration-seconds: 10

ai:
  decision-interval-seconds: 3.0
  decision-lock-seconds: 2.0
  decision-debounce-seconds: 0.5
  request-timeout-ms: 1500
  minimum-confidence: 0.35
  fallback-retreat-hp-ratio: 0.25

doctrine:
  max-line-length: 120

arena:
  dimension: "minecraft:overworld"
  core:
    x: 0
    y: 80
    z: 0
    radius: 3.0
```

The full file is generated with comments and all available options.

The API key is plain text in the server config directory, so keep that directory private and do not commit `config/autobattle.yml`. If `typesafe.api-key` is empty, AutoBattle falls back to the `TYPESAFE_API_KEY` environment variable. If neither is configured, it uses `ScriptedJevClient`.

## TypeSafe Jev

Production AutoBattle uses the TypeSafe System One API with the configured model (default: `jev-latest`).

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

For production Jev testing, set `typesafe.api-key` in `config/autobattle.yml` (or use `TYPESAFE_API_KEY` as a fallback).

## License

MIT
