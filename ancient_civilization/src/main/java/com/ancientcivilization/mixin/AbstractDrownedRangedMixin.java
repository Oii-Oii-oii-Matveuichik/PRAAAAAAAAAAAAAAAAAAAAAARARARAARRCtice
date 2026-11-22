package com.ancientcivilization.mixin;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goals.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractDrowned;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives drowned-family mobs (drowned, V&V murk) bow ranged AI: they shoot arrows
 * when armed with a bow. The bow goal self-disables unless a bow is held.
 */
@Mixin(AbstractDrowned.class)
public class AbstractDrownedRangedMixin implements RangedAttackMob {
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        Arrow arrow = new Arrow(this.getLevel(), this);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + dist * 0.2F, dz, velocity, 1.0F);
        this.getLevel().addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void onRegisterGoals(CallbackInfo ci) {
        this.goalSelector.addGoal(4, new RangedBowAttackGoal<>(this, 1.0, 20, 15.0F));
    }
}
