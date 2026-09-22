package dev.kardane.autobattle.match;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class TeamScoreboard {
    private TeamScoreboard() {
    }

    public static PlayerTeam team(Scoreboard scoreboard, BattleTeam battleTeam) {
        String name = "autobattle_" + battleTeam.name().toLowerCase(java.util.Locale.ROOT);
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }
        team.setColor(battleTeam.robotColor().chatColor());
        return team;
    }

    public static void addRobot(ServerLevel level, String entry, BattleTeam battleTeam) {
        Scoreboard scoreboard = level.getScoreboard();
        scoreboard.addPlayerToTeam(entry, team(scoreboard, battleTeam));
    }

    public static void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        for (BattleTeam battleTeam : BattleTeam.values()) {
            PlayerTeam team = scoreboard.getPlayerTeam(
                "autobattle_" + battleTeam.name().toLowerCase(java.util.Locale.ROOT)
            );
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }
}
