package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Random;

/**
 * F2: melee casts have a chance to spawn as their skeleton-type counterpart
 * (zombie->skeleton, husk->parched, drowned->murk, gelid->stray, thicket->verdant).
 * Equipment is carried over; the replacement is a skeleton-type, so it never re-rolls.
 */
public final class RerollHandler {
    private static final Logger LOGGER = LogManager.getLogger(RerollHandler.class);
    private static final Random RANDOM = new Random();

    private RerollHandler() {
    }

    /** @return true if the mob was replaced and the original join must be cancelled. */
    public static boolean tryReroll(ServerLevel level, LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.reroll.enabled) {
            return false;
        }
        // Never reroll mod-spawned mobs (squad members / wanderers) — they are
        // spawned via addFreshEntity, which fires this same event.
        for (String tag : mob.getTags()) {
            if (tag.startsWith("ancient_civilization:")) {
                return false;
            }
        }
        Map<String, String> mapping = config.reroll.mapping;
        if (mapping.isEmpty() || RANDOM.nextFloat() >= config.reroll.chance) {
            return false;
        }
        String source = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                .map(ResourceLocation::toString)
                .orElse(null);
        String targetId = source == null ? null : mapping.get(source);
        if (targetId == null) {
            return false;
        }
        EntityType<?> targetType = BuiltInRegistries.ENTITY_TYPE
                .getOptional(ResourceLocation.tryParse(targetId))
                .orElse(null);
        if (targetType == null) {
            LOGGER.warn("Reroll target {} not found", targetId);
            return false;
        }
        if (!(mob instanceof Mob sourceMob)) {
            return false;
        }

        try {
            Entity replacement = targetType.create(level);
            if (!(replacement instanceof Mob replacementMob)) {
                return false;
            }
            copyEquipment(sourceMob, replacementMob);
            replacement.setPos(mob.getX(), mob.getY(), mob.getZ());
            replacement.setYRot(mob.getYRot());
            replacement.setXRot(mob.getXRot());
            replacement.setPersistenceRequired(true);
            level.addFreshEntity(replacement);
            LOGGER.debug("Rerolled {} -> {} at {}", source, targetId, mob.blockPosition().toShortString());
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to reroll {} -> {}: {}", source, targetId, e.getMessage());
            return false;
        }
    }

    private static void copyEquipment(Mob from, Mob to) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAIN_HAND) {
                to.setItemInHand(InteractionHand.MAIN_HAND, from.getMainHandItem().copy());
            } else if (slot == EquipmentSlot.OFF_HAND) {
                to.setItemInHand(InteractionHand.OFF_HAND, from.getOffhandItem().copy());
            } else if (slot.isArmor()) {
                to.setItemSlot(slot, from.getItemSlot(slot).copy());
            }
        }
    }
}
