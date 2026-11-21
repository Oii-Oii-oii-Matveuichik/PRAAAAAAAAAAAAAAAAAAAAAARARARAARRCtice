package com.coalarmor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Gameplay hooks; the mixin blocks vanilla extinguishing and water current forces. */
public final class CoalArmorEvents {
    private static final String WATER_CLOUD_KEY = CoalArmorMod.MODID + ":water_cloud";

    private CoalArmorEvents() {
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            if (!entity.level().isClientSide) {
                CoalArmorHelper.tickBurning(living);
            }
            if (CoalArmorHelper.hasFullSet(living)) {
                CoalArmorHelper.applyBuoyancy(living);
            }
        }
        if (!entity.level().isClientSide && entity instanceof AreaEffectCloud cloud
                && cloud.getPersistentData().getBoolean(WATER_CLOUD_KEY)) {
            tickWaterCloud(cloud);
        }
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            if (!living.level().isClientSide) {
                CoalArmorHelper.tickBurning(living);
            }
            if (CoalArmorHelper.hasFullSet(living)) {
                CoalArmorHelper.applyBuoyancy(living);
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !CoalArmorHelper.hasFullSet(entity)) {
            return;
        }
        DamageSource source = event.getSource();
        if (CoalArmorHelper.isFireTagged(source)) {
            event.setAmount(event.getAmount() * 2.0F);
        } else if (!CoalArmorHelper.isTntExplosion(source)) {
            // No extra damage-type exceptions: immunity lasts only while all four pieces survive.
            event.setCanceled(true);
            CoalArmorHelper.damageArmor(entity, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof LivingEntity target) || player == target
                || !CoalArmorHelper.hasFullSet(target)) {
            return;
        }
        ItemStack held = event.getItemStack();
        InteractionHand hand = event.getHand();
        boolean bucket = held.is(Items.WATER_BUCKET)
                && (target.isOnFire() || CoalArmorHelper.isCoalBurning(target));
        boolean flint = held.is(Items.FLINT_AND_STEEL);
        if (!bucket && !flint) {
            return;
        }
        // Acknowledge on both sides so vanilla doesn't also use the held item on a block.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) {
            return;
        }
        if (bucket) {
            CoalArmorHelper.extinguish(target);
            if (!player.isCreative()) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            target.igniteForSeconds(4.0F);
            CoalArmorHelper.latchBurning(target);
            if (!player.isCreative()) {
                held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion) || potion.level().isClientSide) {
            return;
        }
        PotionContents contents = potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!contents.is(Potions.WATER)) {
            return;
        }
        Vec3 hit = event.getRayTraceResult().getLocation();
        for (LivingEntity entity : potion.level().getEntitiesOfClass(LivingEntity.class,
                potion.getBoundingBox().move(hit.subtract(potion.position())).inflate(4.0, 2.0, 4.0))) {
            if (entity.distanceToSqr(hit) < 16 && CoalArmorHelper.hasFullSet(entity)
                    && (CoalArmorHelper.isCoalBurning(entity) || entity.isOnFire())) {
                CoalArmorHelper.latchBurning(entity);
                CoalArmorHelper.tryExtinguishWithBottle(entity);
            }
        }
        if (potion.getItem().is(Items.LINGERING_POTION)) {
            // Vanilla water lingering bottles otherwise create NO cloud at all.
            AreaEffectCloud cloud = new AreaEffectCloud(potion.level(), hit.x, hit.y, hit.z);
            if (potion.getOwner() instanceof LivingEntity owner) {
                cloud.setOwner(owner);
            }
            cloud.setPotionContents(contents);
            cloud.setRadius(3.0F);
            cloud.setWaitTime(10);
            cloud.setDuration(600);
            cloud.setRadiusPerTick(-3.0F / 600.0F);
            cloud.getPersistentData().putBoolean(WATER_CLOUD_KEY, true);
            potion.level().addFreshEntity(cloud);
        }
        // Keep vanilla impact, particles, block extinguishing and axolotl rehydration.
        // The mixin prevents vanilla applyWater from bypassing a failed extinguish roll.
    }

    /** One independent roll per second in the visible, shrinking water cloud. */
    private static void tickWaterCloud(AreaEffectCloud cloud) {
        if (cloud.tickCount < cloud.getWaitTime() || (cloud.tickCount - cloud.getWaitTime()) % 20 != 0) {
            return;
        }
        double radiusSquared = cloud.getRadius() * cloud.getRadius();
        for (LivingEntity entity : cloud.level().getEntitiesOfClass(LivingEntity.class,
                cloud.getBoundingBox().inflate(0, 1.0, 0))) {
            double dx = entity.getX() - cloud.getX();
            double dz = entity.getZ() - cloud.getZ();
            if (dx * dx + dz * dz <= radiusSquared && CoalArmorHelper.hasFullSet(entity)
                    && (CoalArmorHelper.isCoalBurning(entity) || entity.isOnFire())) {
                CoalArmorHelper.tryExtinguishWithBottle(entity);
            }
        }
    }
}
