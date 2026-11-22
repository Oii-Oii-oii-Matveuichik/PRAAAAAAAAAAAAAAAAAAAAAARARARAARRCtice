package com.ancientcivilization.spawn;

import com.ancientcivilization.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public final class EquipmentApplier {
    private EquipmentApplier() {
    }

    public static void apply(LivingEntity mob, ModConfig.Member member) {
        if (member.heldItem != null && !member.heldItem.isEmpty()) {
            Item item = resolveItem(member.heldItem);
            if (item != null && item != Items.AIR) {
                mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            }
        }
        if (member.armor != null) {
            for (Map.Entry<String, String> entry : member.armor.entrySet()) {
                EquipmentSlot slot = slotOf(entry.getKey());
                if (slot == null) {
                    continue;
                }
                Item item = resolveItem(entry.getValue());
                if (item != null && item != Items.AIR) {
                    mob.setItemSlot(slot, new ItemStack(item));
                }
            }
        }
    }

    public static EquipmentSlot slotOf(String key) {
        return switch (key) {
            case "helmet" -> EquipmentSlot.HEAD;
            case "chestplate" -> EquipmentSlot.CHEST;
            case "leggings" -> EquipmentSlot.LEGS;
            case "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    public static Item resolveItem(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }
}
