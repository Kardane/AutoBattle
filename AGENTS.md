# AutoBattle agent guide

This file applies to the repository. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the code map and runtime flows, then read the relevant source before changing behavior. The source and tests take precedence when documentation differs.

## Project and entry points

- Java 21, Minecraft 1.21.8, Fabric dedicated-server mod; vanilla clients need no client mod. Versions are declared in `gradle.properties` and dependencies in `build.gradle`.
- `src/main/java/dev/kardane/autobattle/AutoBattleMod.java` constructs services and registers commands/events. `event/AutoBattleEvents.java` wires ticks, combat, disconnect, and shutdown.
- `match/MatchManager.java` owns match transitions. `match/MatchSession.java` holds per-match state. Do not add a second source of truth for phase, roster, scores, or CORE ownership.
- `README.md` describes player-facing behavior and commands. Check implementations before relying on its details.

## Work safely

- Inspect `git status --short` before editing. Preserve unrelated edits, ignored `run/` files, and local credentials. If Git reports dubious ownership in this Windows checkout, use `git -c safe.directory=C:/Users/parkj/IdeaProjects/AutoBattle ...` for that command; do not change global Git settings.
- Do not commit API keys or runtime files. `run/`, `build/`, `logs/`, and `/config/autobattle/` are local or generated. Default server YAML is embedded in `config/AutoBattleConfigLoader.java` and `config/LanguageConfigLoader.java`.
- For new user-facing text, add the key to `LanguageConfigLoader.DEFAULT_YAML` and use `LanguageService`; preserve placeholders and formatting codes. Existing server `messages.yml` may need migration or a deliberate local update, but ignored runtime files are not a substitute for tracked defaults.
- Keep phase-sensitive logic in `MatchManager` and game-state mutations on the Minecraft server thread. `DoctrineService` and `JevDecisionService` marshal asynchronous completions through `server.execute(...)`; preserve stale-match, round, robot, and candidate checks before applying responses.
- When changing CORE bounds or visuals, inspect both `CoreController.isInsideBounds` and `CoreController.renderBoundary`, plus related tests. When changing tactics, inspect `ValidPlanFactory`, `DecisionComposer`, `JevDecisionService`, and `PlanExecutor` together.
- Native Dialog custom actions are routed by `mixin/ServerCommonPacketListenerImplMixin` to `ui/DialogActionRouter`. Keep free-form Doctrine text in the payload instead of interpolating it into a command.

## Verification and reporting

- Use `src/test/java/dev/kardane/autobattle/` to find focused JUnit coverage. Run `./gradlew.bat test` and `./gradlew.bat build` when the change warrants them; on Windows PowerShell use `.\gradlew.bat`.
- For documentation-only edits, check links, source claims, and `git diff --check`; a build is unnecessary unless source changed.
- Report compile/tests, dedicated-server runtime, client/Dialog interaction, and external TypeSafe/OpenAI calls as separate evidence. Passing Gradle tests does not prove an in-game flow.
- If behavior or a public configuration/command contract changes, update `README.md` and `docs/ARCHITECTURE.md` alongside code.
