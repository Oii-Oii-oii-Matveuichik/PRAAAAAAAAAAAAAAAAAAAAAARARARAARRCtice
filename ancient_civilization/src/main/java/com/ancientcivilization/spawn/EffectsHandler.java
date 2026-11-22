package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Random;

/**
 * Config-driven status effects applied to spawned mobs. Entries are filtered by
 * entity ID, squad ID and/or role tag, with a per-mob chance. Missing effect IDs
 * are skipped silently so the mod keeps working without its dependencies.
 */
public final class EffectsHandler {
    private static final Random RANDOM = new Random();

    private EffectsHandler() {
    }

    public static void apply(ServerLevel level, LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.effects.enabled) {
            return;
        }
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                .map(ResourceLocation::toString)
                .orElse("");
        String squadId = firstTag(mob, "ancient_civilization:squad_");
        String role = firstTag(mob, "ancient_civilization:role_");

        for (ModConfig.EffectEntry entry : config.effects.entries) {
            if (entry == null || entry.effect == null || entry.effect.isEmpty()) {
                continue;
            }
            if (entry.chance < 1.0f && RANDOM.nextFloat() >= entry.chance) {
                continue;
            }
            if (!entry.entities.isEmpty() && !entry.entities.contains(entityId)) {
                continue;
            }
            if (!entry.roles.isEmpty() && (role == null || !entry.roles.contains(role))) {
                continue;
            }
            if (!entry.squads.isEmpty() && (squadId == null || !entry.squads.contains(squadId))) {
                continue;
            }
            ResourceLocation location = ResourceLocation.tryParse(entry.effect);
            if (location == null) {
                continue;
            }
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(location).orElse(null);
            if (effect == null) {
                continue;
            }
            mob.addEffect(new MobEffectInstance(effect, entry.durationTicks, entry.amplifier,
                    entry.durationTicks > 0, true));
        }
    }

    private static String firstTag(LivingEntity mob, String prefix) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(prefix)) {
                return tag.substring(prefix.length());
            }
        }
        return null;
    }
}
