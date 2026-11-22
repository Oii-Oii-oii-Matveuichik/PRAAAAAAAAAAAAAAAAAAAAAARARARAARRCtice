package com.ancientcivilization.wanderer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-dimension record of ruined-portal positions that already had their one-time
 * wanderer roll. One roll per structure instance, never re-checked or respawned.
 */
public class WandererSaveData extends SavedData {
    public static final SavedData.Factory<WandererSaveData> TYPE = new SavedData.Factory<>(
            WandererSaveData::new,
            WandererSaveData::load,
            DataFixTypes.LEVEL);

    private final Set<Long> processedPortals = new HashSet<>();

    public boolean isProcessed(long key) {
        return processedPortals.contains(key);
    }

    public void markProcessed(long key) {
        processedPortals.add(key);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag portals = new CompoundTag();
        for (Long key : processedPortals) {
            portals.putLong(String.valueOf(key), 1L);
        }
        tag.put("processedPortals", portals);
        return tag;
    }

    public static WandererSaveData load(CompoundTag tag, HolderLookup.Provider registries) {
        WandererSaveData data = new WandererSaveData();
        CompoundTag portals = tag.getCompound("processedPortals");
        for (String key : portals.getAllKeys()) {
            data.processedPortals.add(Long.parseLong(key));
        }
        return data;
    }
}
