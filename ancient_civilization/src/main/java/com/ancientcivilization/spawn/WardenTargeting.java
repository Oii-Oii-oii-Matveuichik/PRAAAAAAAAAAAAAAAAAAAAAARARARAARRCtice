package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * F8: Wardens in force-spawned squads target squad members when there are no
 * vanilla (non-mod) players nearby, giving the squad a coherent enemy.
 */
public final class WardenTargeting {
    private WardenTargeting() {
    }

    public static void ensure(LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.warden.enabled) {
            return;
        }
        if (!(mob instanceof Mob warden)) {
            return;
        }
        ResourceLocation mobKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!ResourceLocation.tryParse("minecraft:warden").equals(mobKey)) {
            return;
        }
        if (warden.getTarget() != null) {
            return;
        }
        for (String tag : warden.getTags()) {
            if (tag.startsWith("ancient_civilization:squad_")) {
                MobGoals.addTargetGoal(warden, 2,
                        new NearestAttackableTargetGoal<>(
                                warden,
                                LivingEntity.class,
                                true,
                                (entity, level) -> isSquadMember(entity)));
                return;
            }
        }
    }

    private static boolean isSquadMember(LivingEntity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith("ancient_civilization:squad_")) {
                return true;
            }
        }
        return false;
    }
}
