# AutoBattle architecture

This is a code navigation guide for the Minecraft 1.21.8 Fabric dedicated-server mod. It describes the current source layout; `README.md` covers the player-facing contract. Start at `AutoBattleMod` and follow the owner named below before changing a flow.

## Runtime composition

`fabric.mod.json` registers `dev.kardane.autobattle.AutoBattleMod` as the server entry point. `onInitializeServer()` loads configuration and language files, constructs services, and registers `AutoBattleCommands`, optional Carpet test commands, and `AutoBattleEvents`. Services are wired directly in this composition root.

```mermaid
flowchart LR
  Entry[AutoBattleMod] --> Commands[AutoBattleCommands / CarpetTestCommands]
  Entry --> Events[AutoBattleEvents]
  Commands --> Match[MatchManager]
  Events --> Match
  Match --> Session[MatchSession]
  Match --> Core[CoreController]
  Match --> Robots[RobotRegistry / RobotFactory / PlanExecutor]
  Match --> AI[JevDecisionService]
  Match --> UI[UiCoordinator / DialogService]
  Match --> Logs[MatchLogService]
  AI --> DecisionLogs[JsonlDecisionLogRepository]
  Dialog[DialogActionRouter] --> Doctrine[DoctrineService]
  Dialog --> Match
```

`AutoBattleEvents` calls `MatchManager.tick`, then `PlanExecutor.tick`, then `MatchManager.resolveRobotCombat` at the end of each server tick. It also forwards damage/death, disconnect, and server-stop events. Command registration is in `command/AutoBattleCommands.java`; `command/CarpetTestCommands.java` provides an optional runtime test harness without a compile-time Carpet dependency.

The Carpet harness accepts a per-team size from 1v1 through 8v8; `spawn 2`, `setup 2`, and `verify 2` run a 2v2 smoke test while the no-argument form remains 4v4. Each setup independently selects three distinct strategy lines from a pool of ten for every test bot, then submits that bot-specific combination as its Doctrine.

## State and round flow

`MatchManager` is the transition and orchestration owner. `MatchSession` contains the match ID, players, phase, round state, team scores, robot registry, and CORE controller. `PlayerSlot` owns participant identity, Doctrine, runtime state, and personal score; `RoundState` owns round timing. `MatchPhase` defines:

```text
LOBBY → DOCTRINE_SETUP → COUNTDOWN → ROUND_ACTIVE → ROUND_REVIEW
                                                     ↓          ↘
                                               DOCTRINE_EDIT   FINISHED
                                                     ↓          ↓
                                                 COUNTDOWN   fresh LOBBY
```

The operator starts setup after balanced teams join. Players submit three Doctrine rules. During `ROUND_ACTIVE`, `MatchManager.tick` advances player commands, respawns, regeneration, CORE capture/particles, Jev requests, UI, and round timeout. Review readiness advances to Doctrine edit for another round or to final results. On finish, `MatchManager` writes the result, calls `UiCoordinator.onFinished`, then replaces the session with a fresh lobby. Look at the actual transition methods in `MatchManager` for abort and forfeit branches.

`core/CoreController.java` owns capture occupancy, team ownership, telemetry, and particle boundary. The configured `arena.core.radius` is the half-width/depth of the X/Z CORE square; `isInsideBounds` also checks Y distance. The visual perimeter is built from the same X/Z bounds. Robot movement and target validity use the separate circular `arena.radius` boundary. `CAPTURE_CORE` uses the same `CoreController.isInside` bounds as its arrival condition: once a robot is inside the CORE it stops pushing toward the exact center. While an enemy robot occupies those CORE bounds, the capturing robot temporarily pursues the nearest CORE occupant and attacks when in range, then resumes objective occupancy when the contest clears. `combat/` tracks damage and kill resolution; `robot/` creates and tracks robot entities and respawns; `match/ScoreState` and `TeamScoreState` hold personal and team scoring.

`match/TeamScoreboard` registers players and spawned robots on the same colored vanilla scoreboard teams. `MatchManager` restores prior player teams and removes AutoBattle teams on exit. `PlayerCommandService` temporarily swaps player inventory for three command items during active rounds; `AutoBattleEvents` routes right-clicks through the same `use` method as the command tree, so the once-per-round gate is shared. MatchManager switches viewers to Adventure with flight for item input and restores their view mode after the round. Review and Doctrine edit dialogs block Esc until an action is chosen. Final standings use the name captured in `PlayerSlot` on join.

## AI and Doctrine boundaries

`doctrine/DoctrineService` validates and manages initial submission and one-line edits. `doctrine/DoctrinePresetLibrary` provides twelve Korean three-line bundles; `DialogService` samples three distinct bundles and shows them as read-only examples in the initial setup dialog. Players enter the final three lines manually. `OpenAiDoctrineNormalizer` optionally converts player-authored text to canonical rules; the original remains available for display. Missing configuration or normalization failure uses source text. Asynchronous completion returns to the Minecraft server thread through `server.execute(...)` before changing match state. `ui/DialogActionRouter` handles native Dialog payloads delivered through `mixin/ServerCommonPacketListenerImplMixin`; free-form text is carried as data.

`jev/JevDecisionService` snapshots robot and team state, obtains legal candidates from `tactics/ValidPlanFactory`, and calls a `JevClient`. `AutoBattleMod` selects `TypeSafeJevClient` when an API key is present, otherwise `ScriptedJevClient`. The client response is asynchronous; application runs through `server.execute(...)`. The service rejects stale or inapplicable responses, recomputes legal plans, composes a plan with `DecisionComposer`, and applies it via `tactics/PlanExecutor`. HOLD has a stricter 0.60 confidence floor (or the configured global minimum when higher), and deterministic fallback prioritizes ENGAGE/CHASE target reacquisition before objective, ASSIST, RETREAT, and finally HOLD. A current HOLD is not retained during fallback when another legal action exists. `tactics/RobotController` tracks per-robot plan and decision timing; `PlanExecutor` performs Minecraft-side movement and actions. External text is input to a bounded choice system, not executable game logic.

The provisional and deterministic low-health fallbacks select `RETREAT` while it is legal; if retreat is temporarily unavailable they select `HOLD_POSITION` instead of combat or objective movement. `PlanValidityPolicy` treats RETREAT as invalid once `RetreatPlanner` reports the robot sufficiently safe. On entering that safe state, `RobotController` replaces the completed RETREAT with a server-side `HOLD_POSITION`, stops local movement, and emits an urgent `RETREAT_SAFE` redecision. The resulting snapshot therefore reports HOLD rather than a semantically completed RETREAT, and the next candidate set does not contain RETREAT. Low-health robots can remain in HOLD to recover and later decisions can return them to active plans.

Team tactics add `HOLD_POSITION` and `ASSIST_<ALLY>` as separate tactical plans. HOLD stops navigation for a bounded four-second window while allowing self-defense within the configured `robot.hold-reaction-range` (default 12 blocks), then emits one `HOLD_COMPLETE`. An expired HOLD is excluded from low-confidence fallback; only a deliberate reapplication starts a fresh timer. ASSIST validates a living same-team ally in the same match, rejects retreat targets and assist cycles, supports the ally's active combat target when legal, and otherwise maintains a two-to-four-block support distance. `RobotStateSerializer` creates dedicated ally snapshots with `current_plan`, `combat_target`, `inside_core`, and `under_attack`; `TypeSafeJevClient` serializes those fields and asks for `ally_target` only when more than one ASSIST candidate is legal. Decision logs use schema version 4 and include the ally target choice.

`robot/RobotZombie` hides the vanilla custom name and mounts a server-side `Display.TextDisplay` as its nameplate. The display renders the owner/robot name, a twenty-segment team-colored health bar with gray empty segments, and a Korean tactical action label whose color identifies the action kind (for example, attack, capture, support, hold, or retreat) over a vanilla-style translucent background without a text shadow. It is updated when health or plan changes and is discarded from `onRemoval`, so a dead or removed zombie cannot leave a floating display behind. `arena.radius` is the explicit circular movement boundary. `team-spawns.random-radius` randomizes initial and respawn positions around each team's lane while preserving the configured side and round swapping.

## Config, UI, and persisted output

- `config/AutoBattleConfigLoader.java` embeds the `config/autobattle/config.yml` default and parses it; `ConfigMigrationService` handles older schemas. `LanguageConfigLoader.java` embeds the `config/autobattle/messages.yml` default and `LanguageConfigMigrationService` handles its migration. `ConfigReloadService` loads both and updates the services for `/autobattle admin reload`.
- `ui/UiCoordinator.java` coordinates Sidebar, BossBar, ActionBar, chat, sounds, native Dialogs, and phase-driven BGM. `music/BgmRuntime`, `BgmPackManager`, and `BgmService` provide the OGG/Polymer resource-pack BGM flow: bundled `docs/lobby.ogg` and `docs/bgm1.ogg` through `docs/bgm5.ogg` are seeded into the runtime music directory, assets are scanned into generated sound events, pack readiness is tracked per player, and playback sends normal Minecraft music packets. The generated `lobby` playlist starts automatically before the first round, while the generated `match` playlist starts automatically during rounds and shuffles its tracks on each cycle without immediate repeats. `DialogService` builds the dialogs; `DialogActionRouter` accepts their actions. `review/RoundReviewService` prepares review summaries from decision logs.
- `log/MatchLogService` writes `logs/autobattle/matches/<match-id>.jsonl`. `jev/JsonlDecisionLogRepository` writes `logs/autobattle/decisions/<match-id>.jsonl`. The shared match ID joins both streams. Treat these as runtime records, not repository source.
- The server config can contain API keys. Never copy actual keys into docs, tests, commits, or tool output.

## Change map and proof

| Change | Read first | Useful tests |
| --- | --- | --- |
| Match phases, roster, scores | `match/MatchManager`, `MatchSession`, `PlayerSlot`, `ScoreState` | `src/test/java/dev/kardane/autobattle/match/` |
| Capture geometry or scoring | `core/CoreController`, `CoreState`, `TeamScoreState` | `src/test/java/dev/kardane/autobattle/core/` |
| Jev decisions and tactics | `jev/JevDecisionService`, `DecisionComposer`, `tactics/ValidPlanFactory`, `PlanExecutor` | `src/test/java/dev/kardane/autobattle/jev/` |
| Doctrine or Dialogs | `doctrine/DoctrineService`, `ui/DialogActionRouter`, `DialogService`, `mixin/` | `src/test/java/dev/kardane/autobattle/doctrine/` plus in-game UI check |
| YAML and messages | `config/*Loader`, `ConfigReloadService`, `LanguageService` | `src/test/java/dev/kardane/autobattle/config/` |

Run `.\gradlew.bat test` for unit behavior and `.\gradlew.bat build` for packaging. A server smoke test and a vanilla-client Dialog check are separate runtime evidence. External API success needs a configured test environment and cannot be inferred from scripted-client tests.

## Documentation discrepancy to resolve when touching permissions

At the time of writing, `AutoBattleCommands` gates `/autobattle doctrine view <player>` with `hasPermission(1)`, while `README.md` says permission level 2. Treat the command registration as current behavior; decide the intended contract before changing either. The `/autobattle admin` subtree is gated with `hasPermission(2)`.
