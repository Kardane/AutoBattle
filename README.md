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
config/
└─ autobattle/
   ├─ config.yml
   └─ messages.yml
```

Existing `config/autobattle.yml` installations are automatically moved to `config/autobattle/config.yml` the first time the new loader runs.

Edit this file and either restart the server or run:

```text
/autobattle admin reload
```

The reload command is intentionally restricted to an empty `LOBBY` so an active match cannot change rules underneath running robots. It reloads both YAML files atomically: both files must parse successfully before the new values are applied.

`config.yml` contains the TypeSafe API configuration and the main game tuning values:

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

`messages.yml` controls text shown through the Sidebar, BossBar, ActionBar, chat announcements, and native Dialogs. Dynamic values use placeholders such as `{round}`, `{total_rounds}`, `{seconds}`, `{score}`, `{hp}`, `{color}`, and `{plan}`.

Legacy formatting codes are supported in `messages.yml`: colors `&0`-`&f`, styles `&k`-`&o`, and reset `&r`. Use `&&` to render a literal `&`.

Example:

```yaml
bossbar:
  round: "{round}/{total_rounds} 라운드 | {seconds}초 | CORE {core_owner}{contested_suffix}"

actionbar:
  alive: "체력 {hp}/{max_hp} | 행동 {plan} | 명령 {command} | 점수 {score}"

chat:
  core-captured: "[AutoBattle] {color} 로봇이 CORE를 점령했습니다."
```

The API key is plain text in the server config directory, so keep that directory private and do not commit `config/autobattle/`. If `typesafe.api-key` is empty, AutoBattle falls back to the `TYPESAFE_API_KEY` environment variable. If neither is configured, it uses `ScriptedJevClient`.

## Match logs

Every started match writes an analysis-friendly JSONL file:

```text
logs/autobattle/matches/<match-id>.jsonl
```

Events include match/round start and end, player commands, robot kills, CORE captures, forfeits, final standings, and aborted matches. Participant snapshots include UUID/name, color, Doctrine version/text, and round/total score metrics.

Jev decisions remain in:

```text
logs/autobattle/decisions/<match-id>.jsonl
```

The shared match ID makes the two files easy to join during later analysis.

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

## Carpet bot test harness

Fabric Carpet is optional at runtime. When it is installed, operators can drive a four-player smoke test without four real clients:

```text
/autobattle admin test spawn
/autobattle admin test setup
/autobattle admin test status
```

The harness uses Carpet fake players `ABot1` through `ABot4`. `setup` joins them, marks them ready, submits deterministic Doctrine presets, and advances the match to `COUNTDOWN`.

During a round:

```text
/autobattle admin test command 1 attack
/autobattle admin test command 2 capture
/autobattle admin test command 3 survive
```

After a round, either advance the whole intermission at once:

```text
/autobattle admin test next
```

or split it to test Doctrine editing between phases:

```text
/autobattle admin test review
/execute as ABot1 run autobattle doctrine replace 2 "Focus the weakest enemy"
/autobattle admin test keep
```

Clean up fake players and reset their participation with:

```text
/autobattle admin test cleanup
```

The test commands detect Carpet through the registered `/player` command and do not add a compile-time Carpet dependency.

## Development

```powershell
.\gradlew.bat genSources
.\gradlew.bat build
.\gradlew.bat runServer
```

For production Jev testing, set `typesafe.api-key` in `config/autobattle/config.yml` (or use `TYPESAFE_API_KEY` as a fallback).

## License

MIT
