# AutoBattle

Minecraft Java Edition 1.21.8 Fabric server-side multiplayer AI autobattler.

Players do not directly control their fighters. Each player teaches one robot with three natural-language doctrine rules, observes an automated round, and may revise one doctrine line between rounds.

## MVP

- 4-player FFA baseline
- One zombie robot per player
- Dyed leather chestplate for robot identity
- Melee-only combat
- Central CORE objective
- Five rounds
- One player command per round
- JEV chooses high-level tactical plans; Minecraft server code executes them
- Vanilla clients; no client-side mod required

## Development stage

The first implementation slice establishes the AutoBattle project identity, match/lobby state model, doctrine/score domain model, player lobby commands, and the server-tick lifecycle hook.

Robot spawning, tactical plans, CORE combat, UI, and JEV integration follow as separate implementation slices.

## Stack

- Minecraft 1.21.8
- Fabric Loader 0.18.0
- Fabric API 0.136.0+1.21.8
- Java 21
- Mojang official mappings

## Development

```powershell
.\gradlew.bat genSources
.\gradlew.bat build
.\gradlew.bat runServer
```

## License

MIT
