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

    // Armor textures are standalone TextureManager resources, not block/item-atlas
    // sprites: .png.mcmeta animation alone would NOT animate them in this renderer.
    // Pre-register the frame paths once; selecting a frame allocates no new resources.
    public static final int BURNING_FRAME_COUNT = 48;
    public static final int BURNING_FRAME_TICKS = 2;
    private static final ResourceLocation[][] BURNING_FRAMES = createBurningFrames();

    private static ResourceLocation[][] createBurningFrames() {
        ResourceLocation[][] frames = new ResourceLocation[2][BURNING_FRAME_COUNT];
        for (int layer = 0; layer < frames.length; layer++) {
            for (int frame = 0; frame < BURNING_FRAME_COUNT; frame++) {
                String number = frame < 10 ? "0" + frame : Integer.toString(frame);
                frames[layer][frame] = ResourceLocation.fromNamespaceAndPath(CoalArmorMod.MODID,
                        "textures/models/armor/coal_burning/layer_" + (layer + 1) + "_" + number + ".png");
            }
        }
        return frames;
    }

    /** World time keeps the layers in phase, survives chunk unloads and pauses with the game. */
    public static int burningFrame(long gameTime) {
        return (int) Math.floorMod(gameTime / BURNING_FRAME_TICKS, (long) BURNING_FRAME_COUNT);
    }

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
            return BURNING_FRAMES[innerModel ? 1 : 0][burningFrame(living.level().getGameTime())];
        }
        return null;
    }
}
