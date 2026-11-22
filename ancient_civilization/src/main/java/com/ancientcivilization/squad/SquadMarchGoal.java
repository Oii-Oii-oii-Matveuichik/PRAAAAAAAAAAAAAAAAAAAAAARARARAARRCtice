package com.ancientcivilization.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * F10: moves a squad member toward a fixed village position; stops on arrival
 * and removes itself. Priority-1 march: the mob still fights if a target is in range.
 */
public class SquadMarchGoal extends Goal {
    private final Mob mob;
    private final BlockPos target;
    private final double speed;
    private final double stopDistance;

    private boolean finished;

    public SquadMarchGoal(Mob mob, BlockPos target, double speed, double stopDistance) {
        this.mob = mob;
        this.target = target;
        this.speed = speed;
        this.stopDistance = stopDistance;
    }

    @Override
    public boolean canUse() {
        return !finished;
    }

    @Override
    public boolean canContinueToUse() {
        return !finished && !nearTarget();
    }

    private boolean nearTarget() {
        double dx = target.getX() + 0.5 - mob.getX();
        double dz = target.getZ() + 0.5 - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= stopDistance;
    }

    @Override
    public void tick() {
        if (nearTarget()) {
            finished = true;
            return;
        }
        this.mob.getMoveControl().setWantedPosition(
                target.getX() + 0.5, mob.getY(), target.getZ() + 0.5, this.speed);
    }

    @Override
    public void stop() {
        this.mob.getMoveControl().setWantedPosition(
                mob.getX(), mob.getY(), mob.getZ(), 0.0);
    }
}
