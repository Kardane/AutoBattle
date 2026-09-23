# AutoBattle

Minecraft Java Edition 1.21.8 Fabric server-side multiplayer AI autobattler.

Players do not directly control their fighters. Each player teaches one robot with three natural-language Doctrine rules, observes an automated round, and may revise one Doctrine line between rounds.

## MVP

- RED vs BLUE team battle, from 1v1 through 8v8
- Teams must have equal active player counts before a match can start
- Joining the lobby marks the player ready immediately; an operator explicitly starts Doctrine setup
- One zombie robot per player with stable IDs R1..R8 / B1..B8
- Team-colored leather chestplates
- Melee-only combat
- Central CORE objective
- Five rounds
- One player command per round, activated by right-clicking one of three command items or by `/autobattle command attack|capture|survive`
- TypeSafe Jev chooses high-level tactical plans; Minecraft server code executes them
- Vanilla clients; no client-side mod required

Participants and their robots share a RED or BLUE scoreboard team, so player names have the same team color. During an active round, participants can fly at the viewing position in Adventure mode and use the attack, capture, or survive item in hotbar slots 1–3. The three items share the existing one-command-per-round limit. The original inventory is restored after the round; the original game mode is restored when leaving or finishing the match. Final personal standings show player names.

## Configuration

On first server launch, AutoBattle creates:

```text
config/
└─ autobattle/
   ├─ config.yml
   ├─ messages.yml
   ├─ music.json
   └─ music/
```

Existing `config/autobattle.yml` installations are automatically moved to `config/autobattle/config.yml` the first time the new loader runs. Existing v1 `config.yml` / `messages.yml` files are then migrated automatically to schema v2. User API keys and tuning values are preserved, the previous file is backed up, and the migrated file is validated before atomic replacement.

Edit this file and either restart the server or run:

```text
/autobattle admin reload
```

The reload command can be used during any phase. It reloads both YAML files atomically: both files must parse successfully before the new values are applied, and the current match/session is kept running while the new values are applied immediately.

`config.yml` contains the TypeSafe API configuration and the main game tuning values:

- TypeSafe API key, base URL, and model
- OpenAI Doctrine Normalizer toggle, API key, model, timeout budget, and retry settings
- minimum/maximum team size (default 1..8 per team), round count, round/countdown/respawn/command timing
- AI decision interval, lock, debounce, timeout, minimum confidence, and emergency fallback RETREAT HP threshold
- Doctrine maximum line length
- robot HP, damage, movement/follow range, HOLD reaction range, regeneration
- tactical movement speeds and leash distances
- kill/assist/CORE scoring and assist window
- CORE capture/hold timings
- dimension, CORE position/radius, configurable arena radius, randomized team spawn regions, side swapping, and viewer-ring layout

Example:

```yaml
openai:
  doctrine-normalizer:
    enabled: true
    # Leave empty to use OPENAI_API_KEY.
    api-key: ""
    base-url: "https://api.openai.com"
    model: "gpt-5.6-luna"
    request-timeout-ms: 5000
    total-timeout-ms: 6500
    max-attempts: 2
    retry-backoff-ms: 200

typesafe:
  api-key: "ts_your_key_here"
  base-url: "https://api.typesafe.ai"
  model: "jev-latest"

config-version: 2

match:
  min-team-size: 1
  max-team-size: 8
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
  # RETREAT is always available to Jev.
  # This threshold is only for deterministic server fallback.
  fallback-retreat-hp-ratio: 0.25

doctrine:
  max-line-length: 120

robot:
  max-health: 100.0
  attack-damage: 10.0
  movement-speed: 0.30
  follow-range: 32.0
  # HOLD reacts to an enemy within this many blocks.
  hold-reaction-range: 12.0

arena:
  dimension: "minecraft:overworld"
  core:
    x: 0
    y: 80
    z: 0
    radius: 3.0
  # Circular robot movement boundary centered on the CORE.
  radius: 32.0
  team-spawns:
    axis: "x"
    distance-from-core: 18.0
    member-spacing: 3.0
    y: 80.0
    swap-sides-each-round: true
    # Random spawn offset around each team's lane, in blocks.
    random-radius: 4.0
  viewer-spawn:
    radius: 24.0
    y: 88.0
```

`arena.radius` is the circular boundary used by robot movement, target validity, and retreat planning. `core.radius` remains the separate CORE capture radius. Each robot spawn is randomized within `team-spawns.random-radius` of its team's configured lane; the same resolver is used for round starts and respawns.

The full file is generated with comments and all available options.

### BGM

AutoBattle includes a server-side BGM system based on the OGG/Polymer flow
from [minigame-shader](https://github.com/biryeongtrain/minigame-shader).
Put `.ogg` Vorbis files under `config/autobattle/music/`. AutoBattle adds them
to Polymer's generated resource pack, waits for each player's pack to finish
loading, and then sends normal Minecraft music packets. No client-side mod is
required. BGM starts automatically in the lobby and switches to the match
playlist when a round starts. The bundled `docs/lobby.ogg` and `docs/bgm1.ogg` through
`docs/bgm5.ogg` files are copied into that directory on first startup when a
same-named user file does not already exist.

The generated default playlists are:

- `lobby`: `lobby.ogg`, repeated continuously while the match is waiting to
  start, including doctrine setup.
- `match`: `bgm1.ogg` through `bgm5.ogg`, automatically shuffled and repeated
  after a round starts. A new shuffle avoids repeating the track that just
  finished.

Playback waits for each player's Polymer resource pack acknowledgement and
also attaches the current track to players who become ready after a playlist
has started.

`config/autobattle/music.json` is created automatically:

```json
{
  "autoStart": true
}
```

`autoStart` enables a startup recovery path if the server lifecycle reaches a
ready listener before the lobby playlist is created. Normal lobby and round
phase transitions start their playlists automatically. Optional playlists are stored in
`config/autobattle/music/playlists.json`:

```json
{
  "lobby": {
    "tracks": ["lobby/first.ogg", "lobby/second.ogg"],
    "repeat": true
  }
}
```

The track IDs are generated from their relative paths and can be inspected
with `/autobattle music list`. Operators can control playback with:

```text
/autobattle music list
/autobattle music play <track-id|playlist> [players]
/autobattle music stop [players]
/autobattle music next [players]
/autobattle music global play <track-id|playlist>
/autobattle music global stop
/autobattle music global next
```

After adding or replacing OGG files, restart the server so Polymer rebuilds and
publishes the resource pack. Broken, non-Vorbis, truncated, and chained OGG
streams are skipped with a server log warning.

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

Events include match/round start and end, player commands, robot kills, team CORE captures, forfeits, final team results, personal contribution standings, and aborted matches. Participant snapshots include UUID/name, RED/BLUE team, stable target ID, Doctrine version/text, and personal metrics. Match events also record team scores.

Jev decisions remain in:

```text
logs/autobattle/decisions/<match-id>.jsonl
```

The shared match ID makes the two files easy to join during later analysis. Decision-log schema v4 records team/target identity and team context in addition to the decision trigger, request-time and apply-time legal plans, whether the candidate set changed, each decomposed Jev answer with confidence/probabilities (including the selected ally for `SUPPORT`), the composed/effective plan, latency, request-time HP/CORE state, positions/distances, and API error information.

Participant snapshots preserve both the player-authored Doctrine (`doctrine`) and the canonical Doctrine sent to Jev (`doctrineNormalized`), plus normalization hash/model/status, prompt version, attempt count, latency, HTTP status, and fallback error when applicable.

## TypeSafe Jev

Production AutoBattle uses the TypeSafe System One API with the configured model (default: `jev-latest`).

Jev answers four narrow tactical questions in one System One request; `combat_target`, `pursuit_style`, and `ally_target` are included only when their candidate set requires them:

```text
strategic_intent: FIGHT | SUPPORT | HOLD | CONTROL_CORE | RETREAT
combat_target:    <living enemy target ID, e.g. B3 or R5>
pursuit_style:    ENGAGE | CHASE
ally_target:      <living ally target ID when SUPPORT>
```

Server code deterministically composes those answers into legal plans such as `ENGAGE_B3`, `CHASE_R5`, `ASSIST_R2`, `HOLD_POSITION`, `CAPTURE_CORE`, `DEFEND_CORE`, or `RETREAT`. Same-team robots are never combat candidates, and ASSIST candidates are limited to living same-team allies. HOLD requires at least 0.60 confidence (or the configured global minimum when it is higher); a low-confidence HOLD is replaced by an active ENGAGE/CHASE, objective, ASSIST, or RETREAT fallback when one is legal. Apply-time state is validated again, so a CORE ownership change can recompose `CONTROL_CORE` instead of discarding the entire response. Doctrine text is state data, not executable game logic.

`CAPTURE_CORE` treats entry into the configured CORE bounds as arrival instead of forcing robots to reach the exact center. If a living enemy occupies the CORE, the capturing robot pursues the nearest such enemy inside the objective and resumes occupancy behavior after the contest is cleared. This avoids center-stacking path failures and symmetric stalls where both teams occupy the CORE without entering attack range.

When a robot is below the fallback retreat HP threshold, provisional behavior prefers `RETREAT` even when enemies are already far away. After the retreat planner reaches a safe distance, `RobotController` keeps the `RETREAT` plan, stops navigation, and waits for the normal decision interval instead of immediately switching back to `CAPTURE_CORE` or `DEFEND_CORE`.

Team tactics also expose `HOLD_POSITION` and `ASSIST_<ALLY>`. `HOLD_POSITION` stops at the current position for up to four seconds, reacts to an enemy within `robot.hold-reaction-range` blocks, and then requests a fresh decision. An expired HOLD is not retained by low-confidence fallback; a deliberate high-confidence HOLD response starts a fresh four-second window. `ASSIST_<ALLY>` follows the ally's current combat or objective behavior, shares a nearby enemy when appropriate, or maintains a two-to-four-block support distance. Ally snapshots sent to Jev include the ally's current plan, actual combat target, CORE occupancy, and recent damage state. Jev can therefore select `SUPPORT` with an `ally_target`, or `HOLD` when waiting is strategically preferable.

## Doctrine normalization

When enabled, AutoBattle normalizes all player Doctrine text—regardless of whether it is Korean, English, or another language—into concise canonical English before TypeSafe Jev sees it. The UI continues to show and edit the player's original text.

Normalization happens only when Doctrine is initially submitted or actually edited. `KEEP` does not call OpenAI. Requests use `HttpClient.sendAsync`; Minecraft state is touched only after completion is marshalled back onto the server thread. A player can have at most one normalization pending at a time. Identical normalization keys (source hash + model + prompt version) use single-flight deduplication so concurrent players share one upstream OpenAI request, and successful results are cached for later reuse in the same server process.

The normalizer uses the OpenAI Responses API with Structured Outputs. It is instructed to preserve player intent and explicit numeric thresholds, keep the three rules separate, avoid inventing new goals or conditions, and prefer AutoBattle terms such as `TEAM`, `ALLY`, `ENEMY`, `CORE`, `ENGAGE`, `CHASE`, `CAPTURE_CORE`, `DEFEND_CORE`, `RETREAT`, and `HP` when they accurately match the source.

Only the three Doctrine strings plus fixed game terminology/instructions are sent to OpenAI. Player UUIDs, names, IP addresses, server addresses, TypeSafe credentials, and other match state are not included in the normalizer request. Responses are requested with `store: false`. Any separate OpenAI API data-sharing or promotional-credit setting is controlled at the OpenAI organization/project level rather than by AutoBattle.

Transient OpenAI failures (network/timeout, HTTP 408/429/5xx) may be retried within the configured total deadline. Authentication/request errors are not retried. If normalization still fails, AutoBattle falls back to the original three Doctrine lines and continues the game. Set `openai.doctrine-normalizer.api-key` or the `OPENAI_API_KEY` environment variable to enable live normalization.

## Native Dialog flow

Minecraft 1.21.8 native Dialogs are used for the player-facing training loop:

```text
Players join and are immediately Ready
    ↓
Operator runs /autobattle admin start when RED and BLUE counts are equal (`startround` remains a legacy/debug command)
    ↓
Doctrine Setup Dialog
    - three Korean strategy examples selected from a library of twelve three-line examples
    - examples are shown as text only; enter or edit the three strategy lines manually
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

The review and Doctrine edit dialogs require an explicit action; Esc does not dismiss them. The Doctrine line editor also blocks Esc. The initial Doctrine setup dialog shows three Korean strategy examples chosen from twelve complete three-line bundles; the examples are read-only text and the player enters the final three lines manually.

Free-form Doctrine text is submitted with Minecraft's custom dialog action payload rather than being interpolated into a command string. Spaces, quotes, Korean text and other normal input therefore remain data.

The existing `/autobattle doctrine ...` and `/autobattle review` commands remain available as debugging/fallback paths.

Operators can immediately finish an active match and broadcast its final standings with:

```text
/autobattle admin end
```

Operators can inspect an online participant's submitted Doctrine with:

```text
/autobattle doctrine view <player>
```

The Doctrine view command requires permission level 2 because it reveals another participant's strategy.

## Stack

- Minecraft 1.21.8
- Fabric Loader 0.18.0
- Fabric API 0.136.0+1.21.8
- Polymer 0.13.13+1.21.8 (BGM resource-pack transport)
- Java 21
- Mojang official mappings
- TypeSafe System One / Jev

## Carpet bot test harness

Fabric Carpet is optional at runtime. When it is installed, operators can drive balanced team smoke tests without real clients. The default harness size is 4v4 and the same commands accept a team-size argument from 1v1 through 8v8. The argument is the number of bots on each team:

```text
/autobattle admin test spawn
/autobattle admin test setup
/autobattle admin test status

# 2v2 smoke test
/autobattle admin test spawn 2
/autobattle admin test setup 2
/autobattle admin test verify 2

# Full 8v8 stress setup
/autobattle admin test spawn 8
/autobattle admin test setup 8
```

The harness supports Carpet fake players `ABot1` through `ABot16`. Join order automatically balances RED/BLUE, players are ready on join, and `setup [teamSize]` explicitly enters Doctrine setup, randomly selects three distinct lines from a pool of ten strategy lines for each bot, and advances the match to `COUNTDOWN`. Each bot receives its own random three-line combination; RED/BLUE strategies are not mirrored. Run `cleanup` before switching an existing test match to another team size.

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


### Team battle Carpet verification

Use `/autobattle admin test spawn 2`, `/autobattle admin test setup 2`, then `/autobattle admin test verify 2` for 2v2. Use `4` for 4v4 or `8` for 8v8. During `ROUND_ACTIVE`, verify also checks robot controller/entity team identity and rejects friendly combat targets.
