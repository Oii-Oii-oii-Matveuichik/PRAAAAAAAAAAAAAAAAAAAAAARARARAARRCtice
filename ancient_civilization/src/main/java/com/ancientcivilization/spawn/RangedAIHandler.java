package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;

/**
 * Attaches the self-contained {@link AncientRangedGoal} to skeleton-family and
 * drowned-family mobs on spawn. The goal self-disables unless the mob actually
 * holds a bow or crossbow, so vanilla loadouts are unaffected. Replaces the
 * former mixin-based CrossbowAttackMob / RangedAttackMob interface approach.
 */
public final class RangedAIHandler {
    private RangedAIHandler() {
    }

    public static void attach(ServerLevel level, Mob mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null) {
            return;
        }
        // Skeleton-family (skeleton, stray, wither_skeleton, V&V gelid/thicket/verdant)
        // and drowned-family (drowned, V&V murk) can wield ranged weapons.
        if (mob instanceof AbstractSkeleton || mob instanceof Drowned) {
            MobGoals.addGoal(mob, 4, new AncientRangedGoal(mob, 15.0F));
        }
    }
}
