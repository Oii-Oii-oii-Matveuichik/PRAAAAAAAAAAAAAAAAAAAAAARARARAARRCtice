package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

/**
 * Top-tier gear spawning gated by local difficulty, per world difficulty level.
 * Runs on EntityJoinLevelEvent at NORMAL priority — after InControl's LOWEST handler —
 * so ambient castes are already applied and the top-tier roll decides the final loadout.
 */
public final class LocalDifficultySpawner {
    private static final Logger LOGGER = LogManager.getLogger(LocalDifficultySpawner.class);
    private static final Random RANDOM = new Random();

    /** Undead family eligible for top-tier gear (incl. Variants & Ventures). */
    private static final Set<ResourceLocation> TOP_MOBS = Set.of(
            ResourceLocation.tryParse("minecraft:zombie"),
            ResourceLocation.tryParse("minecraft:husk"),
            ResourceLocation.tryParse("minecraft:drowned"),
            ResourceLocation.tryParse("minecraft:wither_skeleton"),
            ResourceLocation.tryParse("minecraft:skeleton"),
            ResourceLocation.tryParse("minecraft:stray"),
            ResourceLocation.tryParse("minecraft:bogged"),
            ResourceLocation.tryParse("variantsandventures:gelid"),
            ResourceLocation.tryParse("variantsandventures:thicket"),
            ResourceLocation.tryParse("variantsandventures:verdant"),
            ResourceLocation.tryParse("variantsandventures:murk"),
            ResourceLocation.tryParse("variantsandventures:parched"));

    private LocalDifficultySpawner() {
    }

    public static void tryApply(ServerLevel level, LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.localDifficulty.enabled || !config.localDifficulty.topTier.enabled) {
            return;
        }
        ModConfig.TopTier topTier = config.localDifficulty.topTier;

        ResourceLocation mobKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!TOP_MOBS.contains(mobKey)) {
            return;
        }
        ModConfig.LevelConfig levelConfig = config.localDifficulty.levels.forDifficulty(level.getDifficulty());
        if (levelConfig == null || !levelConfig.enabled) {
            return;
        }
        float effective = level.getCurrentDifficultyAt(mob.blockPosition()).getEffectiveDifficulty();
        if (effective < levelConfig.threshold) {
            return;
        }

        applyArmor(mob, topTier);
        applyWeapon(mob, topTier);
    }

    /** All mob entity IDs eligible for top-tier gear rolls (used by the test commands). */
    public static Set<String> mobIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ResourceLocation id : TOP_MOBS) {
            ids.add(id.toString());
        }
        return ids;
    }

    private static void applyArmor(LivingEntity mob, ModConfig.TopTier topTier) {
        List<ModConfig.WeightedItem> helmet = topTier.helmet;
        List<ModConfig.WeightedItem> chestplate = topTier.chestplate;
        List<ModConfig.WeightedItem> leggings = topTier.leggings;
        List<ModConfig.WeightedItem> boots = topTier.boots;

        if (RANDOM.nextFloat() < topTier.chancePerSlot) {
            SlotApplier.applySlot(mob, EquipmentSlot.HEAD, helmet, "helmet");
        }
        if (RANDOM.nextFloat() < topTier.chancePerSlot) {
            SlotApplier.applySlot(mob, EquipmentSlot.CHEST, chestplate, "chestplate");
        }
        if (RANDOM.nextFloat() < topTier.chancePerSlot) {
            SlotApplier.applySlot(mob, EquipmentSlot.LEGS, leggings, "leggings");
        }
        if (RANDOM.nextFloat() < topTier.chancePerSlot) {
            SlotApplier.applySlot(mob, EquipmentSlot.FEET, boots, "boots");
        }
    }

    private static void applyWeapon(LivingEntity mob, ModConfig.TopTier topTier) {
        if (RANDOM.nextFloat() < topTier.chanceWeapon) {
            String itemId = pickWeighted(topTier.weapon);
            if (itemId != null) {
                var item = EquipmentApplier.resolveItem(itemId);
                if (item != null) {
                    mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                } else {
                    LOGGER.warn("Top-tier weapon {} not found, skipping", itemId);
                }
            }
        }
    }

    static String pickWeighted(List<ModConfig.WeightedItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        int total = 0;
        for (ModConfig.WeightedItem item : items) {
            total += Math.max(item.weight, 0);
        }
        if (total <= 0) {
            return null;
        }
        int roll = RANDOM.nextInt(total);
        for (ModConfig.WeightedItem item : items) {
            roll -= Math.max(item.weight, 0);
            if (roll <= 0) {
                return item.item;
            }
        }
        return items.get(items.size() - 1).item;
    }

    /** Small helper to apply one weighted slot without duplicating the resolve logic. */
    static final class SlotApplier {
        private SlotApplier() {
        }

        static void applySlot(LivingEntity mob, EquipmentSlot slot, List<ModConfig.WeightedItem> items, String label) {
            String itemId = pickWeighted(items);
            if (itemId == null) {
                return;
            }
            var item = EquipmentApplier.resolveItem(itemId);
            if (item != null) {
                mob.setItemSlot(slot, new ItemStack(item));
            } else {
                LOGGER.warn("Top-tier {} item {} not found, skipping", label, itemId);
            }
        }
    }
}
