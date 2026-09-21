package dev.kardane.autobattle.combat;

import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DamageRules {
    public boolean allowDamage(
        LivingEntity victim,
        DamageSource source,
        MatchManager matchManager
    ) {
        if (!(victim instanceof RobotZombie victimRobot)) {
            return true;
        }

        if (matchManager.session().phase()
            != MatchPhase.ROUND_ACTIVE) {
            return false;
        }

        if (!victimRobot.matchId().equals(
            matchManager.session().matchId()
        )) {
            return false;
        }

        Entity sourceEntity = source.getEntity();

        if (!(sourceEntity instanceof RobotZombie attackerRobot)) {
            return false;
        }

        if (!attackerRobot.matchId().equals(victimRobot.matchId())) {
            return false;
        }

        return !attackerRobot.ownerUuid()
            .equals(victimRobot.ownerUuid())
            && attackerRobot.team()
                .isEnemy(victimRobot.team());
    }
}
