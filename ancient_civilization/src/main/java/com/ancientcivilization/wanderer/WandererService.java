package com.ancientcivilization.wanderer;

import com.ancientcivilization.config.ModConfig;
import com.ancientcivilization.spawn.EquipmentApplier;
import com.ancientcivilization.spawn.StructureLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F8: stray wanderers at ruined portals. One roll per structure instance (persisted in
 * WandererSaveData); no re-check, no respawn. Rolls once when a player approaches.
 */
public final class WandererService {
    private static final Logger LOGGER = LogManager.getLogger(WandererService.class);
    private static final Random RANDOM = new Random();
    private static final Map<ServerLevel, WandererService> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final WandererSaveData data;
    private long tickCounter;
    private Set<Structure> portalStructures;

    private WandererService(ServerLevel level) {
        this.level = level;
        this.data = level.getDataStorage().computeIfAbsent(
                WandererSaveData.TYPE, "ancient_civilization:wanderers");
    }

    public static WandererService get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, WandererService::new);
    }

    public void tick() {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.wanderers.enabled || level.players().isEmpty()) {
            return;
        }
        tickCounter++;
        if (tickCounter % Math.max(20, config.wanderers.scanIntervalTicks) != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            scanNearPlayer(player);
        }
    }

    private void scanNearPlayer(ServerPlayer player) {
        ModConfig config = ModConfig.INSTANCE;
        int radius = config.wanderers.detectionRadius;
        BlockPos origin = player.blockPosition();
        Set<Structure> portals = portalStructures();
        if (portals.isEmpty()) {
            return;
        }

        for (int dx = -radius; dx <= radius; dx += 16) {
            for (int dz = -radius; dz <= radius; dz += 16) {
                BlockPos pos = origin.offset(dx, 0, dz);
                Structure structure = StructureLookup.at(level, pos, portals);
                if (structure == null) {
                    continue;
                }
                // Optional biome whitelist (empty = any biome).
                if (!config.wanderers.biomes.isEmpty()
                        && !config.wanderers.biomes.contains(level.getBiome(pos).getRegisteredName())) {
                    continue;
                }
                long key = pos.asLong();
                if (data.isProcessed(key)) {
                    continue;
                }
                data.markProcessed(key);
                data.setDirty();

                // Single chance for every portal (0.10 = 10%).
                if (RANDOM.nextFloat() >= config.wanderers.chance) {
                    continue;
                }
                spawnWanderer(pos, config);
            }
        }
    }

    private Set<Structure> portalStructures() {
        if (portalStructures == null) {
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Set<Structure> found = new HashSet<>();
            for (String id : List.of(
                    "minecraft:ruined_portal", "minecraft:ruined_portal_desert",
                    "minecraft:ruined_portal_nether", "minecraft:ruined_portal_underground")) {
                ResourceLocation location = ResourceLocation.tryParse(id);
                if (location != null) {
                    registry.get(ResourceKey.create(Registries.STRUCTURE, location)).map(Holder::value).ifPresent(found::add);
                }
            }
            portalStructures = found;
        }
        return portalStructures;
    }

    private void spawnWanderer(BlockPos pos, ModConfig config) {
        ModConfig.WandererCaste caste = pickCaste(config.wanderers.castes);
        if (caste == null) {
            return;
        }
        ResourceLocation entityLocation = ResourceLocation.tryParse(caste.entity);
        if (entityLocation == null) {
            return;
        }
        var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityLocation).orElse(null);
        if (entityType == null) {
            LOGGER.warn("Wanderer entity {} not found", caste.entity);
            return;
        }
        try {
            Entity entity = entityType.create(level);
            if (!(entity instanceof Mob mob)) {
                return;
            }
            BlockPos ground = adjustToGround(pos);
            mob.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
            ModConfig.Member member = new ModConfig.Member();
            member.role = caste.role;
            member.entity = caste.entity;
            member.heldItem = caste.heldItem;
            member.armor = caste.armor;
            EquipmentApplier.apply(mob, member);
            mob.addTag("ancient_civilization:wanderer");
            if (caste.role != null) {
                mob.addTag("ancient_civilization:role_" + caste.role);
            }
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            LOGGER.debug("Wanderer ({}) spawned at {}", caste.role, pos.toShortString());
        } catch (Exception e) {
            LOGGER.warn("Failed to spawn wanderer at {}: {}", pos.toShortString(), e.getMessage());
        }
    }

    private ModConfig.WandererCaste pickCaste(List<ModConfig.WandererCaste> castes) {
        if (castes == null || castes.isEmpty()) {
            return null;
        }
        int total = 0;
        for (ModConfig.WandererCaste caste : castes) {
            total += Math.max(caste.weight, 0);
        }
        if (total <= 0) {
            return castes.get(0);
        }
        int roll = RANDOM.nextInt(total);
        for (ModConfig.WandererCaste caste : castes) {
            roll -= Math.max(caste.weight, 0);
            if (roll <= 0) {
                return caste;
            }
        }
        return castes.get(castes.size() - 1);
    }

    private BlockPos adjustToGround(BlockPos pos) {
        for (int y = 0; y <= 16; y++) {
            BlockPos p = pos.offset(0, -y, 0);
            var state = level.getBlockState(p);
            if (state.isSolid() && !state.is(Blocks.AIR)) {
                return p.above();
            }
        }
        return pos;
    }
}
