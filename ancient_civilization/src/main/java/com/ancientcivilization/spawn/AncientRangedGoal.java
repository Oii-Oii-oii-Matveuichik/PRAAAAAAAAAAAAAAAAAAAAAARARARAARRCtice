package com.ancientcivilization.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Self-contained ranged goal for crossbow-armed skeletons and bow-armed drowned/murk.
 * Charges the held ranged weapon (vanilla charge animation via startUsingItem) and
 * fires a vanilla arrow on release, mirroring {@code AbstractSkeleton.performRangedAttack}.
 * Replaces the former mixin-based approach — no interface implementation required.
 */
public class AncientRangedGoal extends Goal {
    private final Mob mob;
    private final float attackRadiusSqr;
    private int seeTime;
    private int attackDelay;
    private boolean charging;

    public AncientRangedGoal(Mob mob, float attackRadius) {
        this.mob = mob;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isHoldingRanged() && hasValidTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return isHoldingRanged() && hasValidTarget() && (charging || !mob.getNavigation().isDone());
    }

    private boolean isHoldingRanged() {
        ItemStack main = mob.getMainHandItem();
        return main.getItem() == Items.BOW || main.getItem() == Items.CROSSBOW;
    }

    private boolean hasValidTarget() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void stop() {
        super.stop();
        this.charging = false;
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        boolean sees = mob.getSensing().hasLineOfSight(target);
        boolean wasSeeing = seeTime > 0;
        if (sees != wasSeeing) {
            this.seeTime = 0;
        }
        if (sees) {
            this.seeTime++;
        } else {
            this.seeTime--;
        }

        double distSq = mob.distanceToSqr(target);
        boolean inRange = distSq <= attackRadiusSqr && seeTime >= 5;
        if (!inRange && attackDelay == 0) {
            mob.getNavigation().moveTo(target, canRun() ? 0.9 : 0.45);
        } else {
            mob.getNavigation().stop();
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (!charging) {
            if (inRange) {
                mob.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.charging = true;
            }
        } else {
            int ticks = mob.getTicksUsingItem();
            ItemStack useItem = mob.getUseItem();
            int chargeDuration = useItem.getUseDuration(mob);
            if (ticks >= chargeDuration) {
                mob.releaseUsingItem();
                this.charging = false;
                this.attackDelay = 20 + mob.getRandom().nextInt(20);
                fireArrow(target, useItem);
            }
        }
        if (this.attackDelay > 0) {
            this.attackDelay--;
        }
    }

    private boolean canRun() {
        return !charging;
    }

    private void fireArrow(LivingEntity target, ItemStack weapon) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack projectile = mob.getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, projectile, 1.6F, weapon);
        double dx = target.getX() - mob.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + dist * 0.2F, dz, 1.6F, (float) (14 - level.getDifficulty().getId() * 4));
        level.addFreshEntity(arrow);
    }
}
