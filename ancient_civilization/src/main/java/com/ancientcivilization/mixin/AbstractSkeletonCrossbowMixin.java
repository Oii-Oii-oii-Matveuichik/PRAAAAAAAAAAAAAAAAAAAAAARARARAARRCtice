package com.ancientcivilization.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.ai.goals.RangedCrossbowAttackGoal;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes skeleton-family mobs able to shoot crossbows (archer tier carries crossbows).
 * The crossbow goal is always present but self-disables unless the mob holds a crossbow;
 * the vanilla bow logic stays untouched when a bow is held.
 */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonCrossbowMixin implements CrossbowAttackMob {
    private boolean chargingCrossbow;

    @Override
    public void setChargingCrossbow(boolean charging) {
        this.chargingCrossbow = charging;
    }

    @Override
    public void onCrossbowAttackPerformed() {
        // Skeletons have no crossbow animation state; nothing to play back.
    }

    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
    private void onPerformRangedAttack(LivingEntity target, float velocity, CallbackInfo ci) {
        if (this.getMainHandItem().getItem() instanceof CrossbowItem) {
            this.performCrossbowAttack(target, velocity);
            ci.cancel();
        }
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void onRegisterGoals(CallbackInfo ci) {
        this.goalSelector.addGoal(4, new RangedCrossbowAttackGoal<>(this, 1.0, 15.0F));
    }
}
