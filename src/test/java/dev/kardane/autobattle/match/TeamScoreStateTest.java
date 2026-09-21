package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.core.CoreController;
import dev.kardane.autobattle.robot.RobotRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TeamScoreStateTest {
    @Test
    void accumulatesTeamScoringAndPreservesTotalsAcrossRoundReset() {
        TeamScoreState score = new TeamScoreState();

        score.addKill(5);
        score.addAssist(2);
        score.addCoreCapture(3);
        score.addCoreHoldTicks(40);
        score.addCoreHoldPoint(1);

        assertEquals(11, score.totalScore());
        assertEquals(11, score.roundScore());
        assertEquals(1, score.totalKills());
        assertEquals(1, score.totalAssists());
        assertEquals(1, score.totalCoreCaptures());
        assertEquals(40L, score.roundCoreHoldTicks());

        score.resetRound();

        assertEquals(11, score.totalScore());
        assertEquals(0, score.roundScore());
        assertEquals(1, score.totalKills());
        assertEquals(1, score.totalAssists());
        assertEquals(1, score.totalCoreCaptures());
        assertEquals(0L, score.roundCoreHoldTicks());
    }

    @Test
    void matchWinnerUsesOnlyTeamTotalScoreAndAllowsDraw() {
        MatchSession match = newMatch();

        assertTrue(match.draw());
        assertTrue(match.winnerTeam().isEmpty());

        match.teamScore(BattleTeam.RED).addKill(5);

        assertFalse(match.draw());
        assertEquals(
            BattleTeam.RED,
            match.winnerTeam().orElseThrow()
        );

        match.teamScore(BattleTeam.BLUE).addKill(5);

        assertTrue(match.draw());
        assertTrue(match.winnerTeam().isEmpty());

        match.teamScore(BattleTeam.BLUE).addAssist(2);

        assertEquals(
            BattleTeam.BLUE,
            match.winnerTeam().orElseThrow()
        );
    }

    private MatchSession newMatch() {
        AutoBattleConfig config = AutoBattleConfig.defaults();

        return new MatchSession(
            UUID.randomUUID(),
            new RobotRegistry(),
            new CoreController(
                ArenaConfig.defaults(),
                config.core(),
                config.scoring()
            )
        );
    }
}
