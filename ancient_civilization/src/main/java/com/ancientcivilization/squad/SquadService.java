package com.ancientcivilization.squad;

import com.ancientcivilization.config.ModConfig;
import com.ancientcivilization.spawn.EquipmentApplier;
import com.ancientcivilization.spawn.LocalDifficultySpawner;
import com.ancientcivilization.spawn.MobGoals;
import com.ancientcivilization.spawn.StructureLookup;
import com.ancientcivilization.spawn.WardenTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Squad engine: per-dimension timers, surface-only spawn position search,
 * digging-out animation, and the squad entity spawn with equipment.
 */
public final class SquadService {
    private static final Logger LOGGER = LogManager.getLogger(SquadService.class);
    private static final Random RANDOM = new Random();
    private static final Map<ServerLevel, SquadService> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final SquadSaveData data;
    private final List<SquadAnimation> animations = new ArrayList<>();

    private SquadService(ServerLevel level) {
        this.level = level;
        this.data = level.getDataStorage().computeIfAbsent(
                SquadSaveData.TYPE, "ancient_civilization:squads");
    }

    public static SquadService get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, SquadService::new);
    }

    /**
     * Test hook (test commands): spawn a full squad of the given type centered at
     * {@code center}. Members get the full roster, equipment, tags and persistence,
     * without timers, animation or ruin-buff scaling.
     */
    public void summon(ModConfig.SquadType type, BlockPos center) {
        spawnSquad(type, adjustToGround(center), false, false, false, null);
    }

    public void tick() {
        tickAnimations();

        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.squads.enabled || level.players().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (data.lastSpawnGameTime() > 0
                && now - data.lastSpawnGameTime() < config.squads.globalCooldownTicks) {
            return;
        }

        for (ModConfig.SquadType type : config.squads.types) {
            data.putTimer(type.id, data.timer(type.id) + 1);
            if (data.timer(type.id) >= intervalFor(type)) {
                trySpawn(type);
            }
        }
    }

    private long intervalFor(ModConfig.SquadType type) {
        long interval = data.nextInterval(type.id);
        return interval > 0 ? interval : type.baseTimerTicks;
    }

    private void tickAnimations() {
        for (int i = animations.size() - 1; i >= 0; i--) {
            SquadAnimation animation = animations.get(i);
            animation.tick();
            if (animation.isFinished()) {
                animations.remove(i);
            }
        }
    }

    private void trySpawn(ModConfig.SquadType type) {
        boolean marching = false;
        BlockPos marchTarget = null;
        Optional<BlockPos> spawnPos;

        // F10: ground squads within triggerRadius of a village march to it from a remote spawn.
        MarchOption march = marchOption(type);
        if (march != null) {
            marching = true;
            marchTarget = march.target;
            spawnPos = march.spawnPos;
        } else {
            spawnPos = findSpawnPosition(type);
        }
        if (spawnPos.isEmpty()) {
            // No valid surface spot: back off and try again later.
            data.putTimer(type.id, type.baseTimerTicks / 2);
            return;
        }
        BlockPos pos = spawnPos.get();
        boolean boosted = isNearStructure(type, pos);
        boolean inRuins = isInRuins(pos);
        final boolean marchingFinal = marching;
        final BlockPos marchTargetFinal = marchTarget;
        ModConfig.Animation animConfig = ModConfig.INSTANCE.animation;
        BlockState digBlock = chooseDigBlock(pos);

        if (animConfig.enabled) {
            animations.add(new SquadAnimation(level, pos, digBlock, animConfig,
                    () -> spawnSquad(type, pos, boosted, inRuins, marchingFinal, marchTargetFinal)));
        } else {
            spawnSquad(type, pos, boosted, inRuins, marchingFinal, marchTargetFinal);
        }

        data.putTimer(type.id, 0L);
        data.putNextInterval(type.id, boosted ? type.boostedTimerTicks : type.baseTimerTicks);
        data.setLastSpawnGameTime(level.getGameTime());
        data.setDirty();
        LOGGER.debug("Squad '{}' spawning at {} (boosted={}, ruins={}, marching={}, animation={})",
                type.id, pos.toShortString(), boosted, inRuins, marching, animConfig.enabled);
    }

    /** F10: returns a remote spawn + village target when the squad should march, or null. */
    private MarchOption marchOption(ModConfig.SquadType type) {
        ModConfig.March march = ModConfig.INSTANCE.march;
        if (march == null || !march.enabled || !march.appliesTo.contains(type.id)) {
            return null;
        }
        Optional<ServerPlayer> target = targetPlayer();
        if (target.isEmpty()) {
            return null;
        }
        BlockPos village = findVillageNear(target.get(), march.triggerRadius);
        if (village == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double dist = march.spawnMin + RANDOM.nextDouble() * (march.spawnMax - march.spawnMin);
            BlockPos candidate = village.offset(
                    (int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
            if (isValidSurface(candidate, false)) {
                return new MarchOption(Optional.of(candidate), village);
            }
        }
        return null;
    }

    private record MarchOption(Optional<BlockPos> spawnPos, BlockPos target) {
    }

    /** Scans a grid around the player for a village structure (tag minecraft:village). */
    private BlockPos findVillageNear(ServerPlayer player, int radius) {
        TagKey<Structure> villageTag = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.tryParse("minecraft:village"));
        BlockPos origin = player.blockPosition();
        BlockPos found = level.findNearestMapStructure(villageTag, origin, radius, false);
        return found;
    }

    /** True when the spawn point sits in a ruin structure. */
    private boolean isInRuins(BlockPos pos) {
        ModConfig.RuinBuff ruinBuff = ModConfig.INSTANCE.ruinBuff;
        if (ruinBuff == null || !ruinBuff.enabled || ruinBuff.ruinStructures.isEmpty()) {
            return false;
        }
        Set<Structure> ruins = resolveStructures(ruinBuff.ruinStructures);
        return structureAt(pos, ruins) != null;
    }

    /** Ruin buff = headcount only: spawns countMultiplier times as many members. */
    private int spawnCount(List<ModConfig.Member> members, boolean inRuins) {
        int base = members.size();
        if (!inRuins || base <= 0) {
            return base;
        }
        ModConfig.RuinBuff ruinBuff = ModConfig.INSTANCE.ruinBuff;
        int count = Math.max(base, (int) Math.round(base * ruinBuff.countMultiplier));
        return Math.min(count, 32);
    }

    // ------------------------------------------------------------------
    // Spawn position search
    // ------------------------------------------------------------------

    private Optional<BlockPos> findSpawnPosition(ModConfig.SquadType type) {
        if (type.undergroundAllowed) {
            return findAncientCityPosition();
        }
        Optional<ServerPlayer> target = targetPlayer();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        BlockPos origin = target.get().blockPosition();

        // Ring 32-48 blocks around the target, outside its view.
        for (int r = 32; r <= 48; r += 4) {
            for (int a = 0; a < 24; a++) {
                double angle = a / 24.0 * Math.PI * 2;
                int dx = (int) (r * Math.cos(angle));
                int dz = (int) (r * Math.sin(angle));
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (!isValidSurface(candidate, type.id.equals("ocean"))) {
                    continue;
                }
                if (!type.biomes.isEmpty()
                        && !type.biomes.contains(level.getBiome(candidate).getRegisteredName())) {
                    continue;
                }
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Optional<ServerPlayer> targetPlayer() {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return Optional.empty();
        }
        return players.stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(
                        level.getLevelData().getSpawnPos().getX(),
                        level.getLevelData().getSpawnPos().getY(),
                        level.getLevelData().getSpawnPos().getZ())))
                .or(() -> Optional.of(players.get(0)));
    }

    /**
     * Surface check: solid ground below, air (or water for ocean squads) to stand in,
     * open sky above, and sky light 15 — i.e. outdoors on the surface, never underground.
     */
    private boolean isValidSurface(BlockPos pos, boolean allowWater) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolid() || below.is(Blocks.AIR)) {
            return false;
        }
        if (below.is(Blocks.LAVA) || below.is(Blocks.MAGMA_BLOCK)) {
            return false;
        }
        BlockState at = level.getBlockState(pos);
        if (allowWater) {
            if (!at.isAir() && !at.is(Blocks.WATER)) {
                return false;
            }
        } else {
            if (!at.isAir()) {
                return false;
            }
        }
        BlockState above = level.getBlockState(pos.above());
        if (!above.isAir() && !(allowWater && above.is(Blocks.WATER))) {
            return false;
        }
        // Open sky above = on the surface, never underground.
        return level.canSeeSky(pos);
    }

    private Optional<BlockPos> findAncientCityPosition() {
        Structure ancientCity = resolveStructure("minecraft:ancient_city");
        if (ancientCity == null) {
            return Optional.empty();
        }
        if (data.hasAncientCityCache()) {
            BlockPos cached = new BlockPos(data.ancientCityX(), data.ancientCityY(), data.ancientCityZ());
            if (structureAt(cached, Set.of(ancientCity)) != null) {
                return Optional.of(adjustToGround(cached));
            }
        }
        BlockPos worldSpawn = level.getLevelData().getSpawnPos();
        Set<Structure> target = Set.of(ancientCity);
        for (int r = 32; r <= 384; r += 32) {
            for (int a = 0; a < 24; a++) {
                double angle = a / 24.0 * Math.PI * 2;
                BlockPos p = worldSpawn.offset((int) (r * Math.cos(angle)), 0, (int) (r * Math.sin(angle)));
                if (structureAt(p, target) != null) {
                    BlockPos ground = adjustToGround(p);
                    data.setAncientCity(ground.getX(), ground.getY(), ground.getZ());
                    data.setDirty();
                    return Optional.of(ground);
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Structure proximity (boost)
    // ------------------------------------------------------------------

    private boolean isNearStructure(ModConfig.SquadType type, BlockPos pos) {
        if (type.structures.isEmpty()) {
            return false;
        }
        Set<Structure> targets = resolveStructures(type.structures);
        if (targets.isEmpty()) {
            return false;
        }
        return structureAt(pos, targets) != null;
    }

    /**
     * Returns the first structure from {@code targets} whose bounding box contains
     * {@code pos}, or {@code null} if none match.
     */
    private Structure structureAt(BlockPos pos, Set<Structure> targets) {
        return StructureLookup.at(level, pos, targets);
    }

    private Set<Structure> resolveStructures(List<String> ids) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<Structure> found = new HashSet<>();
        for (String id : ids) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null) {
                registry.get(ResourceKey.create(Registries.STRUCTURE, location)).map(Holder::value).ifPresent(found::add);
            }
        }
        return found;
    }

    private Structure resolveStructure(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .get(ResourceKey.create(Registries.STRUCTURE, location))
                .map(Holder::value)
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Spawn
    // ------------------------------------------------------------------

    private void spawnSquad(ModConfig.SquadType type, BlockPos pos, boolean boosted,
                            boolean inRuins, boolean marching, BlockPos marchTarget) {
        List<ModConfig.Member> members = type.members;
        if (members.isEmpty()) {
            return;
        }
        int count = spawnCount(members, inRuins);
        for (int i = 0; i < count; i++) {
            // Ruin buff: cycle the base roster to reach the boosted headcount.
            ModConfig.Member member = members.get(i % members.size());
            ModConfig.SkeletonMelee melee = ModConfig.INSTANCE.skeletonMelee;
            // F2: melee roles may appear as a skeleton (melee mob) instead of the base entity.
            String entityId = member.entity;
            if (isMeleeArmRole(melee, member.role) && isReplaceable(melee, entityId)
                    && RANDOM.nextFloat() < melee.chance) {
                entityId = melee.skeletonEntity;
            }
            Mob mob = createMob(entityId);
            if (mob == null) {
                continue;
            }
            double angle = 2 * Math.PI * i / count + RANDOM.nextDouble() * 0.5;
            double dist = 0.8 + RANDOM.nextDouble() * 1.6;
            BlockPos p = pos.offset((int) (dist * Math.cos(angle)), 0, (int) (dist * Math.sin(angle)));
            p = adjustToGround(p);
            mob.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            // F2: melee roles are armed with skeleton weaponry (weighted, two-handed priority);
            // armor is unchanged, so a skeleton-melee has the same armor chances as the base melee caste.
            ModConfig.Member equip = new ModConfig.Member();
            equip.role = member.role;
            equip.entity = entityId;
            equip.armor = member.armor;
            equip.heldItem = isMeleeArmRole(melee, member.role)
                    ? rollSkeletonWeapon(melee, member.heldItem)
                    : member.heldItem;
            EquipmentApplier.apply(mob, equip);
            mob.addTag("ancient_civilization:squad");
            mob.addTag("ancient_civilization:squad_" + type.id);
            if (member.role != null) {
                mob.addTag("ancient_civilization:role_" + member.role);
            }
            if (boosted) {
                mob.addTag("ancient_civilization:boosted");
            }
            if (inRuins) {
                mob.addTag("ancient_civilization:ruin_buff");
            }
            if (marching && marchTarget != null) {
                MobGoals.addGoal(mob, 1, new SquadMarchGoal(mob, marchTarget, 0.6,
                        ModConfig.INSTANCE.march.approachDistance));
                mob.addTag("ancient_civilization:marching");
            }
            if ("ancient_city".equals(type.id)) {
                WardenTargeting.ensure(mob);
            }
            mob.setPersistenceRequired();
            if ("hero".equals(member.role)) {
                // Event squads drop nothing.
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (slot.isArmor()) {
                        mob.setDropChance(slot, 0.0f);
                    }
                }
                mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
            level.addFreshEntity(mob);
        }
    }

    // ------------------------------------------------------------------
    // F2: skeleton weaponry + skeleton-melee in squads
    // ------------------------------------------------------------------

    /** True when the role is armed with skeleton weaponry (melee roles only). */
    private static boolean isMeleeArmRole(ModConfig.SkeletonMelee melee, String role) {
        return melee != null && melee.enabled && role != null && melee.applyTo.contains(role);
    }

    /** True when the base melee entity can be replaced by a skeleton. */
    private static boolean isReplaceable(ModConfig.SkeletonMelee melee, String entity) {
        return entity != null && melee.replaceEntities.contains(entity);
    }

    /**
     * Roll a skeleton weapon for a melee unit from the weighted pool (two-handed
     * priority). Falls back to the member's fixed item when the pool is empty or
     * the rolled item is not registered.
     */
    private String rollSkeletonWeapon(ModConfig.SkeletonMelee melee, String fallback) {
        String id = LocalDifficultySpawner.pickWeighted(melee.weapons);
        if (id != null && EquipmentApplier.resolveItem(id) != null) {
            return id;
        }
        return fallback;
    }

    private Mob createMob(String entityId) {
        ResourceLocation location = ResourceLocation.tryParse(entityId);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location)
                .map(type -> {
                    try {
                        Entity entity = type.create(level);
                        if (entity instanceof Mob mob) {
                            return mob;
                        }
                        LOGGER.warn("Squad member {} is not a Mob, skipping", entityId);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to create squad member {}: {}", entityId, e.getMessage());
                    }
                    return null;
                })
                .orElse(null);
    }

    private BlockPos adjustToGround(BlockPos pos) {
        for (int y = 0; y <= 16; y++) {
            BlockPos p = pos.offset(0, -y, 0);
            BlockState state = level.getBlockState(p);
            if (state.isSolid() && !state.is(Blocks.AIR)) {
                return p.above();
            }
        }
        return pos;
    }

    private BlockState chooseDigBlock(BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.isSolid()) {
            return below;
        }
        String biome = level.getBiome(pos).getRegisteredName();
        if (biome.startsWith("minecraft:desert") || biome.startsWith("minecraft:badlands")
                || biome.startsWith("minecraft:savanna") || biome.startsWith("minecraft:windswept_hills")
                || biome.startsWith("minecraft:ocean") || biome.startsWith("minecraft:river")
                || biome.startsWith("minecraft:beach") || biome.startsWith("minecraft:deep_")
                || biome.startsWith("minecraft:warm_") || biome.startsWith("minecraft:lukewarm_")
                || biome.startsWith("minecraft:frozen_")) {
            return Blocks.SAND.defaultBlockState();
        }
        if (biome.startsWith("minecraft:snowy") || biome.startsWith("minecraft:ice_spikes")
                || biome.startsWith("minecraft:snowy_beach")) {
            return Blocks.SNOW.defaultBlockState();
        }
        if (biome.startsWith("minecraft:swamp") || biome.startsWith("minecraft:mangrove")) {
            return Blocks.DIRT.defaultBlockState();
        }
        return Blocks.GRAVEL.defaultBlockState();
    }
}
