package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.BattleTeam;

public record TeamContextSnapshot(
    BattleTeam team,
    int teamScore,
    int enemyTeamScore,
    int aliveAllies,
    int aliveEnemies,
    int alliesInsideCore,
    int enemiesInsideCore
) {
}
