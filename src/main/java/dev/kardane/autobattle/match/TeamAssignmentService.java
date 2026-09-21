package dev.kardane.autobattle.match;

import dev.kardane.autobattle.config.MatchRulesConfig;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class TeamAssignmentService {
    public Optional<Assignment> assign(
        MatchSession match,
        MatchRulesConfig rules
    ) {
        int redCount = count(match, BattleTeam.RED);
        int blueCount = count(match, BattleTeam.BLUE);

        BattleTeam preferred = redCount <= blueCount
            ? BattleTeam.RED
            : BattleTeam.BLUE;

        Optional<Integer> memberIndex = nextMemberIndex(
            match,
            preferred,
            rules.maxTeamSize()
        );

        if (memberIndex.isEmpty()) {
            BattleTeam other = preferred.opponent();
            memberIndex = nextMemberIndex(
                match,
                other,
                rules.maxTeamSize()
            );

            if (memberIndex.isEmpty()) {
                return Optional.empty();
            }

            preferred = other;
        }

        int slotIndex = nextSlotIndex(
            match,
            rules.maxTeamSize() * 2
        );

        if (slotIndex < 0) {
            return Optional.empty();
        }

        return Optional.of(
            new Assignment(
                preferred,
                memberIndex.orElseThrow(),
                slotIndex
            )
        );
    }

    public boolean canStart(
        MatchSession match,
        MatchRulesConfig rules
    ) {
        int red = activeCount(match, BattleTeam.RED);
        int blue = activeCount(match, BattleTeam.BLUE);

        return red == blue
            && red >= rules.minTeamSize()
            && red <= rules.maxTeamSize();
    }

    public boolean balanced(MatchSession match) {
        return activeCount(match, BattleTeam.RED)
            == activeCount(match, BattleTeam.BLUE);
    }

    public int count(
        MatchSession match,
        BattleTeam team
    ) {
        return (int) match.players().stream()
            .filter(slot -> slot.team() == team)
            .count();
    }

    public int activeCount(
        MatchSession match,
        BattleTeam team
    ) {
        return (int) match.players().stream()
            .filter(slot -> !slot.forfeited())
            .filter(slot -> slot.team() == team)
            .count();
    }

    private Optional<Integer> nextMemberIndex(
        MatchSession match,
        BattleTeam team,
        int maxTeamSize
    ) {
        Set<Integer> used = new HashSet<>();

        for (PlayerSlot slot : match.players()) {
            if (slot.team() == team) {
                used.add(slot.memberIndex());
            }
        }

        for (int index = 0; index < maxTeamSize; index++) {
            if (!used.contains(index)) {
                return Optional.of(index);
            }
        }

        return Optional.empty();
    }

    private int nextSlotIndex(
        MatchSession match,
        int capacity
    ) {
        Set<Integer> used = new HashSet<>();

        for (PlayerSlot slot : match.players()) {
            used.add(slot.slotIndex());
        }

        for (int index = 0; index < capacity; index++) {
            if (!used.contains(index)) {
                return index;
            }
        }

        return -1;
    }

    public record Assignment(
        BattleTeam team,
        int memberIndex,
        int slotIndex
    ) {
    }
}
