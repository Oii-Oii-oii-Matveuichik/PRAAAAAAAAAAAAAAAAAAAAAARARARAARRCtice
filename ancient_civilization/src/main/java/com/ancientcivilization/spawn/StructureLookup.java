package com.ancientcivilization.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;
import java.util.Set;

/**
 * Structure detection by scanning surrounding chunks. A structure start is stored only
 * in its origin chunk, so nearby chunks must be inspected; the structure's bounding box
 * is then tested for intersection with the query position.
 */
public final class StructureLookup {
    private StructureLookup() {
    }

    /**
     * Returns the first structure from {@code targets} whose bounding box contains
     * {@code pos}, or {@code null} if none match.
     */
    public static Structure at(ServerLevel level, BlockPos pos, Set<Structure> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        BoundingBox probe = new BoundingBox(pos);
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = level.getChunkAt(new BlockPos((cx + dx) << 4, pos.getY(), (cz + dz) << 4));
                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (targets.contains(entry.getKey())
                            && start.isValid()
                            && start.getBoundingBox().intersects(probe)) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }
}
