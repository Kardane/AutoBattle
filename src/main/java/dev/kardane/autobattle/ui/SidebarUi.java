package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.match.MatchSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Objects;

public final class SidebarUi {
    private static final String OBJECTIVE_NAME =
        "autobattle_team";

    private final LanguageService language;
    private Objective objective;

    public SidebarUi(LanguageService language) {
        this.language = Objects.requireNonNull(
            language,
            "language"
        );
    }

    public void create(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        Objective existing = scoreboard.getObjective(
            OBJECTIVE_NAME
        );

        objective = existing != null
            ? existing
            : scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                language.component("sidebar.title"),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
            );

        objective.setDisplayName(
            language.component("sidebar.title")
        );

        scoreboard.setDisplayObjective(
            DisplaySlot.SIDEBAR,
            objective
        );
    }

    public void update(
        MinecraftServer server,
        MatchSession match
    ) {
        if (objective == null) {
            create(server);
        }

        Scoreboard scoreboard = server.getScoreboard();

        for (BattleTeam team : BattleTeam.values()) {
            ScoreHolder holder = ScoreHolder.forNameOnly(
                "autobattle_team_"
                    + team.name().toLowerCase()
            );

            ScoreAccess score =
                scoreboard.getOrCreatePlayerScore(
                    holder,
                    objective
                );

            int total = match.teamScore(team).totalScore();

            score.set(total);
            score.display(
                language.component(
                    "sidebar.team-entry",
                    "team",
                    team.name(),
                    "score",
                    total
                ).copy().withStyle(
                    team.robotColor().chatColor()
                )
            );
        }
    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        if (objective != null) {
            scoreboard.setDisplayObjective(
                DisplaySlot.SIDEBAR,
                null
            );

            scoreboard.removeObjective(objective);
            objective = null;
            return;
        }

        Objective existing = scoreboard.getObjective(
            OBJECTIVE_NAME
        );

        if (existing != null) {
            scoreboard.removeObjective(existing);
        }
    }
}
