package dev.kardane.autobattle.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BattleTeamTest {
    @Test
    void onlyOpposingTeamIsEnemy() {
        assertFalse(BattleTeam.RED.isEnemy(BattleTeam.RED));
        assertFalse(BattleTeam.BLUE.isEnemy(BattleTeam.BLUE));
        assertFalse(BattleTeam.RED.isEnemy(null));
        assertFalse(BattleTeam.BLUE.isEnemy(null));

        assertTrue(BattleTeam.RED.isEnemy(BattleTeam.BLUE));
        assertTrue(BattleTeam.BLUE.isEnemy(BattleTeam.RED));

        assertEquals(BattleTeam.BLUE, BattleTeam.RED.opponent());
        assertEquals(BattleTeam.RED, BattleTeam.BLUE.opponent());
    }

    @Test
    void targetIdsAreStableAndTeamScoped() {
        assertEquals("R1", BattleTeam.RED.targetId(0));
        assertEquals("R8", BattleTeam.RED.targetId(7));
        assertEquals("B1", BattleTeam.BLUE.targetId(0));
        assertEquals("B8", BattleTeam.BLUE.targetId(7));
    }
}
