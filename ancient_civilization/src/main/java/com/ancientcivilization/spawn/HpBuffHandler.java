package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;

/**
 * F4: gear-independent HP buff for the undead family (incl. V&V and squads).
 * Illagers, creepers, warden and bosses are never affected.
 */
public final class HpBuffHandler {
    /** Same eligible set as the top-tier spawner: the undead family incl. V&V. */
    private static final Set<ResourceLocation> UNDEAD = Set.of(
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

    private HpBuffHandler() {
    }

    public static void buff(ServerLevel level, LivingEntity mob) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.hpBuff.enabled) {
            return;
        }
        ResourceLocation mobKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!UNDEAD.contains(mobKey)) {
            return;
        }
        AttributeInstance attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double value = Math.max(config.hpBuff.min, attribute.getValue() * config.hpBuff.percent);
        attribute.addPermanentModifier(new AttributeModifier(
                ResourceLocation.tryParse("ancient_civilization:hp_buff"),
                value,
                AttributeModifier.Operation.ADD_VALUE));
    }
}
