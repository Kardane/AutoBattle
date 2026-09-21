package dev.kardane.autobattle.config;

public final class EffectiveConfigSummary {
    private EffectiveConfigSummary() {
    }

    public static String describe(AutoBattleConfig config) {
        var arena = config.arena();
        var core = arena.corePos();
        var spawns = arena.teamSpawns();
        var viewer = arena.viewerSpawn();

        return "teamSize="
            + config.minTeamSize()
            + ".."
            + config.maxTeamSize()
            + ", core=("
            + core.getX()
            + ","
            + core.getY()
            + ","
            + core.getZ()
            + "), coreRadius="
            + arena.coreRadius()
            + ", teamSpawn={axis="
            + spawns.axis()
            + ", y="
            + spawns.y()
            + ", distance="
            + spawns.distanceFromCore()
            + ", spacing="
            + spawns.memberSpacing()
            + ", swap="
            + spawns.swapSidesEachRound()
            + "}, viewer={y="
            + viewer.y()
            + ", radius="
            + viewer.radius()
            + "}, robot={damage="
            + config.robot().attackDamage()
            + ", engageLeash="
            + config.robot().engageLeashDistance()
            + ", chaseLeash="
            + config.robot().chaseLeashDistance()
            + "}, typesafeKeyConfigured="
            + !config.typesafe().apiKey().isBlank()
            + ", openAiKeyConfigured="
            + !config.doctrineNormalizer().apiKey().isBlank();
    }
}
