package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.core.CoreController;
import dev.kardane.autobattle.robot.RobotRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TeamAssignmentServiceTest {
    private final TeamAssignmentService service =
        new TeamAssignmentService();

    @Test
    void assignsAlternatingBalancedIdsUpToEightVsEight() {
        MatchSession match = newMatch();
        var rules = AutoBattleConfig.defaults().match();
        List<String> ids = new ArrayList<>();

        for (int index = 0; index < 16; index++) {
            TeamAssignmentService.Assignment assignment =
                service.assign(match, rules).orElseThrow();

            PlayerSlot slot = new PlayerSlot(
                UUID.randomUUID(),
                assignment.team(),
                assignment.memberIndex(),
                assignment.slotIndex()
            );

            match.addPlayer(slot);
            ids.add(slot.targetId());
            assertTrue(slot.ready());
        }

        assertEquals(
            List.of(
                "R1", "B1", "R2", "B2",
                "R3", "B3", "R4", "B4",
                "R5", "B5", "R6", "B6",
                "R7", "B7", "R8", "B8"
            ),
            ids
        );

        assertEquals(8, service.count(match, BattleTeam.RED));
        assertEquals(8, service.count(match, BattleTeam.BLUE));
        assertTrue(service.canStart(match, rules));
        assertTrue(service.assign(match, rules).isEmpty());
    }

    @Test
    void everyBalancedSizeFromOneToEightCanStart() {
        var rules = AutoBattleConfig.defaults().match();

        for (int teamSize = 1; teamSize <= 8; teamSize++) {
            MatchSession match = newMatch();

            for (int index = 0;
                 index < teamSize * 2;
                 index++) {
                addAssigned(match, rules);
            }

            assertEquals(
                teamSize,
                service.activeCount(match, BattleTeam.RED)
            );
            assertEquals(
                teamSize,
                service.activeCount(match, BattleTeam.BLUE)
            );
            assertTrue(
                service.canStart(match, rules),
                "Expected " + teamSize + "v" + teamSize
                    + " to be startable"
            );
        }
    }

    @Test
    void unbalancedLobbyCannotStart() {
        MatchSession match = newMatch();
        var rules = AutoBattleConfig.defaults().match();

        addAssigned(match, rules);
        addAssigned(match, rules);
        addAssigned(match, rules);

        assertEquals(2, service.activeCount(match, BattleTeam.RED));
        assertEquals(1, service.activeCount(match, BattleTeam.BLUE));
        assertFalse(service.balanced(match));
        assertFalse(service.canStart(match, rules));
    }

    @Test
    void lobbyLeaveReusesLowestMissingMemberId() {
        MatchSession match = newMatch();
        var rules = AutoBattleConfig.defaults().match();

        List<PlayerSlot> slots = new ArrayList<>();

        for (int index = 0; index < 8; index++) {
            slots.add(addAssigned(match, rules));
        }

        PlayerSlot r3 = slots.stream()
            .filter(slot -> slot.targetId().equals("R3"))
            .findFirst()
            .orElseThrow();

        match.removePlayer(r3.playerUuid());

        TeamAssignmentService.Assignment replacement =
            service.assign(match, rules).orElseThrow();

        assertEquals(BattleTeam.RED, replacement.team());
        assertEquals(2, replacement.memberIndex());

        PlayerSlot replacementSlot = new PlayerSlot(
            UUID.randomUUID(),
            replacement.team(),
            replacement.memberIndex(),
            replacement.slotIndex()
        );

        assertEquals("R3", replacementSlot.targetId());
    }

    @Test
    void readinessCannotBeToggledOff() {
        PlayerSlot slot = new PlayerSlot(
            UUID.randomUUID(),
            BattleTeam.RED,
            0,
            0
        );

        assertTrue(slot.ready());
        slot.setReady(false);
        assertTrue(slot.ready());
    }

    private PlayerSlot addAssigned(
        MatchSession match,
        dev.kardane.autobattle.config.MatchRulesConfig rules
    ) {
        TeamAssignmentService.Assignment assignment =
            service.assign(match, rules).orElseThrow();

        PlayerSlot slot = new PlayerSlot(
            UUID.randomUUID(),
            assignment.team(),
            assignment.memberIndex(),
            assignment.slotIndex()
        );

        match.addPlayer(slot);
        return slot;
    }

    private MatchSession newMatch() {
        AutoBattleConfig config = AutoBattleConfig.defaults();

        CoreController core = new CoreController(
            ArenaConfig.defaults(),
            config.core(),
            config.scoring()
        );

        return new MatchSession(
            UUID.randomUUID(),
            new RobotRegistry(),
            core
        );
    }
}
