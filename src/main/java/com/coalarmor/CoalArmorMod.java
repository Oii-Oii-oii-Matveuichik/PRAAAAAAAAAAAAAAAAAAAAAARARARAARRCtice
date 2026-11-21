package com.coalarmor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CoalArmorMod.MODID)
public class CoalArmorMod {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "coal_armor";

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CoalArmorMod(IEventBus modEventBus) {
        // Register the Deferred Register to the mod event bus so items get registered
        CoalArmorItems.MATERIALS.register(modEventBus);
        CoalArmorItems.ITEMS.register(modEventBus);

        // Register the item to a creative tab
        modEventBus.addListener(CoalArmorItems::addCreative);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(CoalArmorEvents.class);
        CoalArmorHelper.registerCleanup();
    }
}
