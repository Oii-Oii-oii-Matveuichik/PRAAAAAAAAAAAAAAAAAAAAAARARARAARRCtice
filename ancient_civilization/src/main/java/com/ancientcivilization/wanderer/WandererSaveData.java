package com.ancientcivilization.wanderer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-dimension record of ruined-portal positions that already had their one-time
 * wanderer roll. One roll per structure instance, never re-checked or respawned.
 */
public class WandererSaveData extends SavedData {
    public static final SavedDataType<WandererSaveData> TYPE = new SavedDataType<>(
            WandererSaveData.class, WandererSaveData::new, WandererSaveData::save, WandererSaveData::load);

    private final Set<Long> processedPortals = new HashSet<>();

    public boolean isProcessed(long key) {
        return processedPortals.contains(key);
    }

    public void markProcessed(long key) {
        processedPortals.add(key);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag portals = new CompoundTag();
        for (Long key : processedPortals) {
            portals.putLong(String.valueOf(key), 1L);
        }
        tag.put("processedPortals", portals);
        return tag;
    }

    public static WandererSaveData load(CompoundTag tag) {
        WandererSaveData data = new WandererSaveData();
        CompoundTag portals = tag.getCompound("processedPortals");
        for (String key : portals.getAllKeys()) {
            data.processedPortals.add(Long.parseLong(key));
        }
        return data;
    }
}
