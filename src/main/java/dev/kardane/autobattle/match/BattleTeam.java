package dev.kardane.autobattle.match;

import dev.kardane.autobattle.robot.RobotColor;

public enum BattleTeam {
    RED("R", RobotColor.RED),
    BLUE("B", RobotColor.BLUE);

    private final String targetPrefix;
    private final RobotColor robotColor;

    BattleTeam(
        String targetPrefix,
        RobotColor robotColor
    ) {
        this.targetPrefix = targetPrefix;
        this.robotColor = robotColor;
    }

    public String targetPrefix() {
        return targetPrefix;
    }

    public RobotColor robotColor() {
        return robotColor;
    }

    public BattleTeam opponent() {
        return this == RED ? BLUE : RED;
    }

    public boolean isEnemy(BattleTeam other) {
        return other != null && other != this;
    }

    public String targetId(int memberIndex) {
        if (memberIndex < 0 || memberIndex >= 8) {
            throw new IllegalArgumentException(
                "memberIndex must be between 0 and 7"
            );
        }

        return targetPrefix + (memberIndex + 1);
    }
}
