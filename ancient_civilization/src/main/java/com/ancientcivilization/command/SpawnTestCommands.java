package com.ancientcivilization.command;

import com.ancientcivilization.config.ModConfig;
import com.ancientcivilization.spawn.LocalDifficultySpawner;
import com.ancientcivilization.squad.SquadService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Test commands: summon every squad type and every mob type the mod can produce.
 *
 * <pre>
 * /ancientcivilization spawn squad &lt;type&gt; [count]   — summon N squads of one type around the player
 * /ancientcivilization spawn squad all [count]          — summon N squads of every configured type
 * /ancientcivilization spawn mob &lt;entity&gt; [count]     — summon N mobs of one entity type (full mod processing)
 * /ancientcivilization spawn mob all [count]            — summon N of every mob type the mod can spawn
 * </pre>
 *
 * Summoned mobs go through {@code EntityJoinLevelEvent}, so all spawn handlers
 * (armor remap, HP buff, top-tier gear, effects, ranged AI) apply to them.
 */
public final class SpawnTestCommands {
    private static final Logger LOGGER = LogManager.getLogger(SpawnTestCommands.class);
    private static final String COMMAND = "ancientcivilization";

    private SpawnTestCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(COMMAND)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("squad")
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                                .defaultValue(1)
                                                .executes(ctx -> {
                                                    String typeId = StringArgumentType.getArgument(ctx, "type");
                                                    int count = IntegerArgumentType.getArgument(ctx, "count");
                                                    if (typeId.equals("all")) {
                                                        return summonAllSquads(ctx.getSource(), count);
                                                    }
                                                    return summonSquad(ctx.getSource(), typeId, count);
                                                }))))
                        .then(Commands.literal("mob")
                                .then(Commands.argument("entity", StringArgumentType.string())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .defaultValue(1)
                                                .executes(ctx -> {
                                                    String entityId = StringArgumentType.getArgument(ctx, "entity");
                                                    int count = IntegerArgumentType.getArgument(ctx, "count");
                                                    if (entityId.equals("all")) {
                                                        return summonAllMobs(ctx.getSource(), count);
                                                    }
                                                    return summonMob(ctx.getSource(), entityId, count);
                                                }))))));
    }

    // ------------------------------------------------------------------
    // Squads
    // ------------------------------------------------------------------

    private static int summonSquad(CommandSourceStack source, String typeId, int count) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null) {
            source.sendFailure(Component.literal("ancient_civilization config is not loaded yet"));
            return 0;
        }
        ModConfig.SquadType type = null;
        for (ModConfig.SquadType candidate : config.squads.types) {
            if (candidate.id.equals(typeId)) {
                type = candidate;
                break;
            }
        }
        if (type == null) {
            source.sendFailure(Component.literal(
                    "Unknown squad type '" + typeId + "'. Available: " + squadIds(config)));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.getLevel();
        int summoned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos center = scatter(level, player.blockPosition());
            SquadService.get(level).summon(type, center);
            summoned++;
        }
        source.sendSuccess(Component.literal(
                "Summoned '" + typeId + "' squad x" + summoned + " near " + player.blockPosition().toShortString()), true);
        return 1;
    }

    private static int summonAllSquads(CommandSourceStack source, int count) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || config.squads.types.isEmpty()) {
            source.sendFailure(Component.literal("No squad types configured"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.getLevel();
        List<String> ids = new ArrayList<>();
        int summoned = 0;
        for (ModConfig.SquadType type : config.squads.types) {
            for (int i = 0; i < count; i++) {
                BlockPos center = scatter(level, player.blockPosition());
                SquadService.get(level).summon(type, center);
                summoned++;
            }
            ids.add(type.id);
        }
        source.sendSuccess(Component.literal(
                "Summoned squads x" + summoned + " of every type [" + String.join(", ", ids) + "]"), true);
        return 1;
    }

    private static String squadIds(ModConfig config) {
        List<String> ids = new ArrayList<>();
        for (ModConfig.SquadType type : config.squads.types) {
            ids.add(type.id);
        }
        return String.join(", ", ids);
    }

    // ------------------------------------------------------------------
    // Mobs
    // ------------------------------------------------------------------

    private static int summonMob(CommandSourceStack source, String entityId, int count) {
        ResourceLocation location = ResourceLocation.tryParse(entityId);
        EntityType<?> type = location == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown entity type '" + entityId + "'"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        int summoned = spawnMobs(player.getLevel(), type, player, count);
        source.sendSuccess(Component.literal(
                "Summoned " + summoned + "x " + entityId + " near " + player.blockPosition().toShortString()), true);
        return summoned > 0 ? 1 : 0;
    }

    private static int summonAllMobs(CommandSourceStack source, int count) {
        Set<String> ids = allMobIds();
        if (ids.isEmpty()) {
            source.sendFailure(Component.literal("No mob types found for this mod"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        int summoned = 0;
        for (String id : ids) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            EntityType<?> type = location == null ? null
                    : BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
            if (type == null) {
                continue;
            }
            summoned += spawnMobs(player.getLevel(), type, player, count);
        }
        source.sendSuccess(Component.literal(
                "Summoned " + summoned + " mobs (" + ids.size() + " types: " + String.join(", ", ids) + ")"), true);
        return summoned > 0 ? 1 : 0;
    }

    /** Core mob spawn: create, scatter slightly around the player, let the join event process it. */
    private static int spawnMobs(ServerLevel level, EntityType<?> type, ServerPlayer player, int count) {
        int summoned = 0;
        for (int i = 0; i < count; i++) {
            try {
                Entity entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                entity.setPos(player.getX() + (level.getRandom().nextDouble() - 0.5) * 4.0,
                        player.getY() + 1,
                        player.getZ() + (level.getRandom().nextDouble() - 0.5) * 4.0);
                if (entity instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }
                level.addFreshEntity(entity);
                summoned++;
            } catch (Exception e) {
                LOGGER.warn("Failed to summon {}: {}", type.getRegistryName(), e.getMessage());
            }
        }
        return summoned;
    }

    /** All entity IDs the mod can spawn: squad members, skeleton-melee, wanderers, top-tier mobs. */
    static Set<String> allMobIds() {
        Set<String> ids = new LinkedHashSet<>();
        ModConfig config = ModConfig.INSTANCE;
        if (config != null) {
            for (ModConfig.SquadType type : config.squads.types) {
                for (ModConfig.Member member : type.members) {
                    if (member.entity != null) {
                        ids.add(member.entity);
                    }
                }
            }
            if (config.skeletonMelee.enabled && config.skeletonMelee.skeletonEntity != null) {
                ids.add(config.skeletonMelee.skeletonEntity);
            }
            for (ModConfig.WandererCaste caste : config.wanderers.castes) {
                if (caste.entity != null) {
                    ids.add(caste.entity);
                }
            }
        }
        ids.addAll(LocalDifficultySpawner.mobIds());
        return ids;
    }

    private static BlockPos scatter(ServerLevel level, BlockPos origin) {
        return origin.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
    }
}
