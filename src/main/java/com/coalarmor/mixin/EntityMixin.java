package com.coalarmor.mixin;

import com.coalarmor.CoalArmorHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Narrow hooks: retain vanilla fire cadence and disable water current forces at source. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
    private void coalArmor$keepBurning(int ticks, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide && self instanceof LivingEntity living
                && CoalArmorHelper.hasFullSet(living)) {
            if (ticks > 0) {
                CoalArmorHelper.latchBurning(living);
            } else if (CoalArmorHelper.isCoalBurning(living)) {
                // Includes water, rain, snow and failed vanilla water-bottle extinguishing.
                // Allowed extinguishing removes the latch BEFORE calling clearFire().
                ci.cancel();
            }
        }
    }

    // This overload and call were added by NeoForge, so they must not be remapped.
    @Redirect(method = "updateFluidHeightAndDoFluidPushing()V", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid(Lnet/neoforged/neoforge/fluids/FluidType;)Z"))
    private boolean coalArmor$ignoreWaterCurrent(Entity entity, FluidType type) {
        if (type == NeoForgeMod.WATER_TYPE.value() && entity instanceof LivingEntity living
                && CoalArmorHelper.hasFullSet(living)) {
            return false;
        }
        return entity.isPushedByFluid(type);
    }
}
