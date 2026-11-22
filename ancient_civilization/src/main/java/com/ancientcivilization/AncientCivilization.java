package com.ancientcivilization;

import com.ancientcivilization.command.SpawnTestCommands;
import com.ancientcivilization.config.ConfigLoader;
import com.ancientcivilization.config.ModConfig;
import com.ancientcivilization.event.CommonEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(AncientCivilization.MODID)
public class AncientCivilization {
    public static final String MODID = "ancient_civilization";

    public AncientCivilization(IEventBus modEventBus) {
        ModConfig.INSTANCE = ConfigLoader.load();
        CommonEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(SpawnTestCommands::register);
    }
}
