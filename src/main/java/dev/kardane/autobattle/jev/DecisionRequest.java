package dev.kardane.autobattle.jev;

import java.util.List;

public record DecisionRequest(
    DecisionContext context,
    RobotDecisionSnapshot snapshot,
    List<String> validPlanIds
) {
    public DecisionRequest {
        validPlanIds = List.copyOf(validPlanIds);
    }
}
