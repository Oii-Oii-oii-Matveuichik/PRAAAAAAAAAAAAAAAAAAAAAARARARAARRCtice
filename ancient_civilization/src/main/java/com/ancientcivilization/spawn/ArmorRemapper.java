package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * F3: remaps heavy iron armor to copper/iron/golden by biome temperature (and mesa/nether),
 * and adjusts copper oxidation stages by humidity. Only affects heavy-tier pieces;
 * diamond/netherite/echo/siftite are never touched.
 */
public final class ArmorRemapper {
    private static final Logger LOGGER = LogManager.getLogger(ArmorRemapper.class);
    private static final Random RANDOM = new Random();

    private ArmorRemapper() {
    }

    public static void remap(ServerLevel level, LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.armorRemap.enabled) {
            return;
        }
        ResourceLocation biomeLocation = level.getBiome(mob.blockPosition()).getRegistryName();
        String biome = biomeLocation.toString();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }
            ItemStack stack = mob.getItemSlot(slot);
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .map(ResourceLocation::toString)
                    .orElse("");
            String newId = remapItem(itemId, slotName(slot), biome,
                    level.getBiome(mob.blockPosition()).getTemperature(), level, mob);
            if (newId != null) {
                Item item = EquipmentApplier.resolveItem(newId);
                if (item != null) {
                    mob.setItemSlot(slot, new ItemStack(item));
                } else {
                    LOGGER.warn("Remap target {} not found, keeping {}", newId, itemId);
                }
            }
        }
    }

    private static String remapItem(String itemId, String suffix, String biome, float temperature,
                                    ServerLevel level, LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        // heavy_iron -> material remap by biome
        if (itemId.equals("luckysarmory:heavy_iron_" + suffix)) {
            String material = pickMaterial(biome, temperature, config);
            if (material.equals("copper")) {
                return copperItem(suffix, pickStage(biome, level, mob, config));
            }
            return "luckysarmory:heavy_" + material + "_" + suffix;
        }
        // existing heavy copper -> adjust oxidation stage
        if (itemId.startsWith("armory_bridge:heavy_copper")) {
            return copperItem(suffix, pickStage(biome, level, mob, config));
        }
        return null;
    }

    private static String pickMaterial(String biome, float temperature, ModConfig config) {
        ModConfig.ArmorRemap remap = config.armorRemap;
        if (biome.startsWith("minecraft:nether")) {
            return weighted(remap.nether);
        }
        if (biome.contains("mesa") || biome.contains("badlands") || biome.contains("windswept_hills")) {
            return weighted(remap.mesa);
        }
        if (temperature <= 0.15f) {
            return weighted(remap.cold);
        }
        if (temperature < 0.75f) {
            return weighted(remap.temperate);
        }
        return weighted(remap.hot);
    }

    private static String weighted(Map<String, Double> weights) {
        double total = 0;
        for (double value : weights.values()) {
            total += value;
        }
        if (total <= 0) {
            return "iron";
        }
        double roll = RANDOM.nextDouble() * total;
        double cumulative = 0;
        // Prefer copper/iron/golden order for stable iteration.
        for (String key : new String[]{"copper", "iron", "golden"}) {
            Double value = weights.get(key);
            if (value == null) {
                continue;
            }
            cumulative += value;
            if (roll <= cumulative) {
                return key;
            }
        }
        return "golden";
    }

    private static String copperItem(String suffix, String stage) {
        if (stage.isEmpty() || "base".equals(stage)) {
            return "armory_bridge:heavy_copper_" + suffix;
        }
        return "armory_bridge:heavy_copper_" + stage + "_" + suffix;
    }

    private static String pickStage(String biome, ServerLevel level, LivingEntity mob,
                                    ModConfig config) {
        ModConfig.ArmorRemap remap = config.armorRemap;
        String mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                .map(ResourceLocation::toString)
                .orElse("");
        // Always fully oxidized: drowned/murk, ancient city, swamp-like biomes.
        if (mobId.equals("minecraft:drowned") || mobId.equals("variantsandventures:murk")) {
            return "oxidized";
        }
        if (isInAncientCity(level, mob.blockPosition())) {
            return "oxidized";
        }
        if (contains(remap.alwaysOxidizedBiomes, biome)) {
            return "oxidized";
        }
        if (contains(remap.wetBiomes, biome)) {
            return weightedStage(remap.oxidationWet);
        }
        if (contains(remap.dryBiomes, biome)) {
            return weightedStage(remap.oxidationDry);
        }
        return weightedStage(remap.oxidationTemperate);
    }

    private static String weightedStage(Map<String, Double> weights) {
        double total = 0;
        for (double value : weights.values()) {
            total += value;
        }
        if (total <= 0) {
            return "base";
        }
        double roll = RANDOM.nextDouble() * total;
        double cumulative = 0;
        for (String key : new String[]{"base", "exposed", "weathered", "oxidized"}) {
            Double value = weights.get(key);
            if (value == null) {
                continue;
            }
            cumulative += value;
            if (roll <= cumulative) {
                return key;
            }
        }
        return "base";
    }

    private static boolean isInAncientCity(ServerLevel level, net.minecraft.core.BlockPos pos) {
        try {
            Structure structure = level.getStructureAt(pos);
            if (structure == null) {
                return false;
            }
            return level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getOptional(ResourceLocation.tryParse("minecraft:ancient_city"))
                    .map(structure::equals)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean contains(List<String> list, String value) {
        for (String entry : list) {
            if (entry.equals(value) || value.startsWith(entry + "/")) {
                return true;
            }
        }
        return false;
    }

    static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> "";
        };
    }
}
