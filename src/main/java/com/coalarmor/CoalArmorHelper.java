package com.coalarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Full-set mechanics. Partial sets keep only vanilla armor attributes. */
public final class CoalArmorHelper {
    // NeoForge persists this compound with the entity, including logout/world reload.
    private static final String BURNING_KEY = CoalArmorMod.MODID + ":burning";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private CoalArmorHelper() {
    }

    public static void registerCleanup() {
        NeoForge.EVENT_BUS.register(CoalArmorHelper.class);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        extinguish(event.getEntity());
    }

    public static boolean isCoalArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CoalArmorItem;
    }

    public static boolean hasFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(CoalArmorItems.COAL_HELMET.get())
                && entity.getItemBySlot(EquipmentSlot.CHEST).is(CoalArmorItems.COAL_CHESTPLATE.get())
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(CoalArmorItems.COAL_LEGGINGS.get())
                && entity.getItemBySlot(EquipmentSlot.FEET).is(CoalArmorItems.COAL_BOOTS.get());
    }

    public static boolean isCoalBurning(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(BURNING_KEY);
    }

    public static void latchBurning(LivingEntity entity) {
        entity.getPersistentData().putBoolean(BURNING_KEY, true);
    }

    /** Refill only at the end of a cycle: never pin the fire counter at a multiple of 20. */
    public static void tickBurning(LivingEntity entity) {
        if (!hasFullSet(entity)) {
            entity.getPersistentData().remove(BURNING_KEY);
            return;
        }
        if (entity.getRemainingFireTicks() > 0) {
            latchBurning(entity);
        }
        if (isCoalBurning(entity)) {
            if (entity.getRemainingFireTicks() <= 1) {
                entity.setRemainingFireTicks(101);
            }
            entity.setSharedFlagOnFire(true);
        }
    }

    /** Remove the latch before clearFire so the mixin allows this operation. */
    public static void extinguish(LivingEntity entity) {
        entity.getPersistentData().remove(BURNING_KEY);
        entity.clearFire();
        entity.setSharedFlagOnFire(false);
    }

    public static boolean isFireTagged(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE);
    }

    public static boolean isTntExplosion(DamageSource source) {
        return source.getDirectEntity() instanceof PrimedTnt
                || source.getDirectEntity() instanceof MinecartTNT;
    }

    /** Vanilla-like wear, including Unbreaking and break animations. */
    public static void damageArmor(LivingEntity entity, float damage) {
        if (damage <= 0) {
            return;
        }
        int loss = Math.max(1, (int) (damage / 4.0F));
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (isCoalArmor(stack)) {
                stack.hurtAndBreak(loss, entity, slot);
            }
        }
    }

    public static int totalFireProtectionLevel(LivingEntity entity) {
        Holder<Enchantment> fireProtection = entity.level().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FIRE_PROTECTION);
        int total = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (isCoalArmor(stack)) {
                total += EnchantmentHelper.getItemEnchantmentLevel(fireProtection, stack);
            }
        }
        return total;
    }

    /** 10% per total Fire Protection level; cap at 100%. */
    public static double extinguishChance(LivingEntity entity) {
        return Math.min(1.0, totalFireProtectionLevel(entity) * 0.10);
    }

    public static boolean tryExtinguishWithBottle(LivingEntity entity) {
        double chance = extinguishChance(entity);
        if (chance > 0 && entity.getRandom().nextDouble() < chance) {
            extinguish(entity);
            return true;
        }
        return false;
    }

    /**
     * Surface support, on both sides for player movement prediction. Lift feet to the
     * highest touching water surface, but never teleport through a solid ceiling.
     * Deep-water equipping lifts the wearer gradually until the surface is reached.
     * Current forces themselves are disabled by EntityMixin (not approximated here).
     */
    public static void applyBuoyancy(LivingEntity entity) {
        if (entity.isPassenger()) {
            return;
        }
        AABB box = entity.getBoundingBox();
        double surface = Double.NEGATIVE_INFINITY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                for (int y = Mth.floor(box.minY - 0.05); y <= Mth.floor(box.minY + 0.25); y++) {
                    pos.set(x, y, z);
                    FluidState fluid = entity.level().getFluidState(pos);
                    if (fluid.is(FluidTags.WATER)) {
                        double height = y + fluid.getHeight(entity.level(), pos);
                        if (height >= box.minY - 0.05) {
                            surface = Math.max(surface, height);
                        }
                    }
                }
            }
        }
        if (!Double.isFinite(surface)) {
            return;
        }
        double lift = Math.min(0.3, Math.max(0, surface - entity.getY()));
        if (lift > 0 && entity.level().noCollision(entity, box.move(0, lift, 0))) {
            entity.setPos(entity.getX(), entity.getY() + lift, entity.getZ());
        }
        entity.setSwimming(false);
        entity.resetFallDistance();
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, Math.max(0, motion.y), motion.z);
    }
}
