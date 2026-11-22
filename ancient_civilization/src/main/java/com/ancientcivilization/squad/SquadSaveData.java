package com.ancientcivilization.squad;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-dimension squad state: per-type timers, cooldowns and the cached ancient-city position.
 */
public class SquadSaveData extends SavedData {
    public static final SavedData.Factory<SquadSaveData> TYPE = new SavedData.Factory<>(
            SquadSaveData::new,
            SquadSaveData::load,
            DataFixTypes.LEVEL);

    /** Squad type id -> ticks since its last event. */
    private final Map<String, Long> timers = new HashMap<>();
    /** Squad type id -> interval used for the next event. */
    private final Map<String, Long> nextIntervals = new HashMap<>();
    private long lastSpawnGameTime;
    private int ancientCityX = Integer.MIN_VALUE;
    private int ancientCityY;
    private int ancientCityZ;

    public long timer(String id) {
        return timers.getOrDefault(id, 0L);
    }

    public void putTimer(String id, long ticks) {
        timers.put(id, ticks);
    }

    public long nextInterval(String id) {
        return nextIntervals.getOrDefault(id, 0L);
    }

    public void putNextInterval(String id, long ticks) {
        nextIntervals.put(id, ticks);
    }

    public long lastSpawnGameTime() {
        return lastSpawnGameTime;
    }

    public void setLastSpawnGameTime(long gameTime) {
        this.lastSpawnGameTime = gameTime;
    }

    public boolean hasAncientCityCache() {
        return ancientCityX != Integer.MIN_VALUE;
    }

    public int ancientCityX() {
        return ancientCityX;
    }

    public int ancientCityY() {
        return ancientCityY;
    }

    public int ancientCityZ() {
        return ancientCityZ;
    }

    public void setAncientCity(int x, int y, int z) {
        this.ancientCityX = x;
        this.ancientCityY = y;
        this.ancientCityZ = z;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Map.Entry<String, Long> entry : timers.entrySet()) {
            tag.putLong("timer." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : nextIntervals.entrySet()) {
            tag.putLong("interval." + entry.getKey(), entry.getValue());
        }
        tag.putLong("lastSpawnGameTime", lastSpawnGameTime);
        if (hasAncientCityCache()) {
            tag.putInt("ancientCityX", ancientCityX);
            tag.putInt("ancientCityY", ancientCityY);
            tag.putInt("ancientCityZ", ancientCityZ);
        }
        return tag;
    }

    public static SquadSaveData load(CompoundTag tag, HolderLookup.Provider registries) {
        SquadSaveData data = new SquadSaveData();
        for (String key : tag.getAllKeys()) {
            if (key.startsWith("timer.")) {
                data.timers.put(key.substring("timer.".length()), tag.getLong(key));
            } else if (key.startsWith("interval.")) {
                data.nextIntervals.put(key.substring("interval.".length()), tag.getLong(key));
            }
        }
        data.lastSpawnGameTime = tag.getLong("lastSpawnGameTime");
        if (tag.contains("ancientCityX")) {
            data.ancientCityX = tag.getInt("ancientCityX");
            data.ancientCityY = tag.getInt("ancientCityY");
            data.ancientCityZ = tag.getInt("ancientCityZ");
        }
        return data;
    }
}
