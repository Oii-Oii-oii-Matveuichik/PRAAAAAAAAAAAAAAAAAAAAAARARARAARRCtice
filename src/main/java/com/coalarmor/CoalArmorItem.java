package com.coalarmor;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * Coal armor piece. Burns as fuel (burn time equals the coal used to craft it) and
 * renders with a burning texture while the wearer is coal-burning.
 */
public class CoalArmorItem extends ArmorItem {

    private static final ResourceLocation BURNING_LAYER_1 = ResourceLocation.fromNamespaceAndPath(
            CoalArmorMod.MODID, "textures/models/armor/coal_burning_layer_1.png");
    private static final ResourceLocation BURNING_LAYER_2 = ResourceLocation.fromNamespaceAndPath(
            CoalArmorMod.MODID, "textures/models/armor/coal_burning_layer_2.png");

    private final int fuelBurnTime;

    public CoalArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, int fuelBurnTime, Item.Properties properties) {
        super(material, type, properties);
        this.fuelBurnTime = fuelBurnTime;
    }

    /** Burn time in ticks; equals the amount of coal used to craft this piece (coal = 1600 ticks). */
    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return this.fuelBurnTime;
    }

    /**
     * While the wearer is coal-burning (full set + on fire) the armor renders with the
     * burning coal texture; otherwise the default coal texture from the material is used.
     */
    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        if (entity instanceof LivingEntity living && living.isOnFire() && CoalArmorHelper.hasFullSet(living)) {
            return innerModel ? BURNING_LAYER_2 : BURNING_LAYER_1;
        }
        return null;
    }
}
