package dev.kardane.autobattle.ui;

import dev.kardane.autobattle.config.LanguageService;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SidebarUi {
    private static final String OBJECTIVE_NAME =
        "autobattle";

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

        List<PlayerSlot> standings = match.players()
            .stream()
            .sorted(
                Comparator
                    .comparingInt(
                        (PlayerSlot slot) ->
                            slot.score().totalScore()
                    )
                    .reversed()
                    .thenComparingInt(
                        PlayerSlot::slotIndex
                    )
            )
            .toList();

        for (int index = 0;
             index < standings.size();
             index++) {
            PlayerSlot slot = standings.get(index);

            ScoreHolder holder = ScoreHolder.forNameOnly(
                "autobattle_"
                    + slot.color()
                        .name()
                        .toLowerCase()
            );

            ScoreAccess score =
                scoreboard.getOrCreatePlayerScore(
                    holder,
                    objective
                );

            score.set(slot.score().totalScore());
            score.display(
                language.component(
                    "sidebar.entry",
                    "rank",
                    index + 1,
                    "color",
                    slot.color().name(),
                    "score",
                    slot.score().totalScore()
                ).copy().withStyle(
                    slot.color().chatColor()
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
