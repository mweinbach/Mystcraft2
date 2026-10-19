package net.mystcraft.minecraft.age;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.mystcraft.minecraft.fissure.StarFissureGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarFissureFootprintTest {
    @Test void preservesPairedRowsContinuityAndDeterministicNoiseAtModernMinimumHeight() {
        BlockPos origin = new BlockPos(16, -64, 32);
        for (long seed = 0; seed < 128; seed++) {
            List<BlockPos> footprint = StarFissureGenerator.footprint(origin, seed);
            assertEquals(footprint, StarFissureGenerator.footprint(origin, seed));
            assertEquals(footprint.size(), new java.util.HashSet<>(footprint).size());
            Map<Integer, List<Integer>> rows = new HashMap<>();
            for (BlockPos pos : footprint) {
                assertEquals(-64, pos.getY());
                rows.computeIfAbsent(pos.getZ(), ignored -> new java.util.ArrayList<>()).add(pos.getX());
            }
            assertTrue(rows.size() >= 20 && rows.size() <= 34);
            for (int z = 32; z < 32 + rows.size(); z += 2) {
                assertEquals(rows.get(z), rows.get(z + 1));
                if (z > 32) assertTrue(rows.get(z).stream().anyMatch(rows.get(z - 1)::contains),
                        "adjacent source noise bands must overlap");
            }
            assertEquals(List.of(16), rows.get(32));
        }
    }
    @Test void blockedSiteSelectsDeterministicTranslatedGeometryAndBoundsSearch() {
        List<BlockPos> original = StarFissureGenerator.footprint(new BlockPos(16, -64, 32), 17);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        List<BlockPos> moved = StarFissureGenerator.selectCandidate(original, site -> attempts.incrementAndGet() == 2);
        assertEquals(2, attempts.get());
        assertEquals(original.stream().map(pos -> pos.offset(-48, 0, -48)).toList(), moved);
        assertEquals(original, StarFissureGenerator.selectCandidate(original, site -> true));
        attempts.set(0);
        assertTrue(StarFissureGenerator.selectCandidate(original, site -> { attempts.incrementAndGet(); return false; }).isEmpty());
        assertEquals(25, attempts.get(), "exhausted search must remain bounded");
    }
}
