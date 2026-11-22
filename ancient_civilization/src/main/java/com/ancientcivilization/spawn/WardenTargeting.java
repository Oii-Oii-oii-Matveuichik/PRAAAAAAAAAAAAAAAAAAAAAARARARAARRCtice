package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goals.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Warden;

/**
 * F7: the ancient-city squad targets the warden with priority equal to the player's —
 * both are priority 1, so the nearer of the two wins (mandatory warden priority).
 */
public final class WardenTargeting {
    private WardenTargeting() {
    }

    public static void apply(Mob mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.warden.enabled) {
            return;
        }
        mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Warden.class, false));
    }
}
