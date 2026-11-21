package com.coalarmor;

import java.util.EnumMap;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Coal armor material and items.
 *
 * <p>Durability matches golden armor (material durability factor 7). Defense is between
 * iron and diamond (helmet 2, chestplate 7, leggings 6, boots 3; total 18 vs iron 15 / diamond 20).
 * The set bonuses (immunity, fire vulnerability, buoyancy) only apply to the full set.</p>
 */
public final class CoalArmorItems {

    /** Gold armor durability factor: helmet 77, chestplate 112, leggings 105, boots 91. */
    private static final int GOLD_DURABILITY_FACTOR = 7;

    /** Burn time of one coal item in ticks. */
    private static final int COAL_BURN_TIME = 1600;

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, CoalArmorMod.MODID);
    public static final Holder<ArmorMaterial> COAL_MATERIAL = MATERIALS.register("coal", CoalArmorItems::createMaterial);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CoalArmorMod.MODID);

    public static final DeferredItem<CoalArmorItem> COAL_HELMET = ITEMS.register("coal_helmet",
            () -> new CoalArmorItem(COAL_MATERIAL, ArmorItem.Type.HELMET, COAL_BURN_TIME * 5,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(GOLD_DURABILITY_FACTOR)).stacksTo(1)));
    public static final DeferredItem<CoalArmorItem> COAL_CHESTPLATE = ITEMS.register("coal_chestplate",
            () -> new CoalArmorItem(COAL_MATERIAL, ArmorItem.Type.CHESTPLATE, COAL_BURN_TIME * 8,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(GOLD_DURABILITY_FACTOR)).stacksTo(1)));
    public static final DeferredItem<CoalArmorItem> COAL_LEGGINGS = ITEMS.register("coal_leggings",
            () -> new CoalArmorItem(COAL_MATERIAL, ArmorItem.Type.LEGGINGS, COAL_BURN_TIME * 7,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(GOLD_DURABILITY_FACTOR)).stacksTo(1)));
    public static final DeferredItem<CoalArmorItem> COAL_BOOTS = ITEMS.register("coal_boots",
            () -> new CoalArmorItem(COAL_MATERIAL, ArmorItem.Type.BOOTS, COAL_BURN_TIME * 4,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(GOLD_DURABILITY_FACTOR)).stacksTo(1)));

    private CoalArmorItems() {
    }

    private static ArmorMaterial createMaterial() {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, 3);
        defense.put(ArmorItem.Type.LEGGINGS, 6);
        defense.put(ArmorItem.Type.CHESTPLATE, 7);
        defense.put(ArmorItem.Type.HELMET, 2);
        // Layer asset "coal" -> textures/models/armor/coal_layer_1.png (outer) and coal_layer_2.png (legs).
        List<ArmorMaterial.Layer> layers = List.of(new ArmorMaterial.Layer(
                ResourceLocation.fromNamespaceAndPath(CoalArmorMod.MODID, "coal")));
        return new ArmorMaterial(
                defense,
                15, // enchantability
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(Items.COAL),
                layers,
                1.0F, // toughness (between iron 0 and diamond 2)
                0.0F); // knockback resistance
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(COAL_HELMET);
            event.accept(COAL_CHESTPLATE);
            event.accept(COAL_LEGGINGS);
            event.accept(COAL_BOOTS);
        }
    }
}
