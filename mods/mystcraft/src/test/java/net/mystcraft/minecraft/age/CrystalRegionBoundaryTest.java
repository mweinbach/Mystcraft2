package net.mystcraft.minecraft.age;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.mystcraft.minecraft.age.generation.DeferredCrystalWrites;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalRegionBoundaryTest {
    @Test void exactChunkBoundariesAndNegativeCoordinatesRespectTheRealWriteRadius() {
        ChunkPos origin = new ChunkPos(0, 0);
        assertTrue(DeferredCrystalWrites.withinRadius(origin, new BlockPos(31, 70, -16), 1));
        assertFalse(DeferredCrystalWrites.withinRadius(origin, new BlockPos(32, 70, -16), 1));
        assertFalse(DeferredCrystalWrites.withinRadius(origin, new BlockPos(-17, 70, 0), 1));
        assertTrue(DeferredCrystalWrites.withinRadius(new ChunkPos(-2, -3), new BlockPos(-17, 70, -33), 0));
        assertFalse(DeferredCrystalWrites.withinRadius(origin, BlockPos.ZERO, -1));
        assertFalse(DeferredCrystalWrites.withinRadius(new ChunkPos(Integer.MIN_VALUE, 0), new BlockPos(Integer.MAX_VALUE, 70, 0), 1),
                "distance arithmetic must not wrap into a permitted neighboring chunk");
    }
}
