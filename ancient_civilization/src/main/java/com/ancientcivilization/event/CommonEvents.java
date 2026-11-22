package com.ancientcivilization.event;

import com.ancientcivilization.spawn.ArmorRemapper;
import com.ancientcivilization.spawn.EffectsHandler;
import com.ancientcivilization.spawn.HpBuffHandler;
import com.ancientcivilization.spawn.LocalDifficultySpawner;
import com.ancientcivilization.spawn.RangedAIHandler;
import com.ancientcivilization.squad.SquadService;
import com.ancientcivilization.wanderer.WandererService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register(IEventBus bus) {
        // NORMAL runs after InControl's LOWEST handler: we see the final equipment set by InControl.
        bus.addListener(EventPriority.NORMAL, CommonEvents::onEntityJoin);
        bus.addListener(CommonEvents::onLevelTickPre);
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Only mobs — players, villagers and other living entities must stay untouched
        // (armor remap / effects would otherwise alter player loadouts on join).
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        // F3 heavy-armor remap + copper oxidation, F4 HP buff, top-tier by local difficulty.
        ArmorRemapper.remap(level, mob);
        HpBuffHandler.buff(level, mob);
        LocalDifficultySpawner.tryApply(level, mob);
        // Generic config-driven effects (entities/roles/squads filtered).
        EffectsHandler.apply(level, mob);
        // Ranged AI for skeleton/drowned families (self-disables unless a bow/crossbow is held).
        RangedAIHandler.attach(level, mob);
    }

    private static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SquadService.get(level).tick();
            WandererService.get(level).tick();
        }
    }
}
