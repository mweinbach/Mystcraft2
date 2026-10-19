package net.mystcraft.minecraft.age.generation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassicNormalDensityTest {
    /** Golden values extracted independently from pinned classic method and old octave implementation. */
    @Test void normalAndAmplifiedPreserveSourceBiomeBoundaryDensity() {
        double[][] expected = {
            {1376.5411869993911, -1.6187991404890631, -10, -305.92729414722965, -6.96372812403607, -10},
            {623.5539820743923, 10.985603494266998, -10, -144.11438837383832, 5.001812552770424, -10}
        };
        int[] indices = {0, 8, 16, 97, 212, 424};
        for (int mode = 0; mode < 2; mode++) {
            var density = new ClassicNormalDensity(123456789, mode == 1, (x, z) -> switch (Math.floorMod(x * 31 + z * 17, 3)) {
                case 0 -> new ClassicNormalDensity.Profile(.125F, .05F);
                case 1 -> new ClassicNormalDensity.Profile(1, .5F);
                default -> new ClassicNormalDensity.Profile(-1, .1F);
            });
            double[] values = density.lattice(-1, -2);
            for (int i = 0; i < indices.length; i++) assertEquals(expected[mode][i], values[indices[i]], 1e-10);
        }
    }
    @Test void amplifiedChangesPositiveLandButLeavesEntirelyOceanicDensityUnchanged() {
        ClassicNormalDensity.Biomes ocean = (x, z) -> new ClassicNormalDensity.Profile(-1, .1F);
        assertArrayEquals(new ClassicNormalDensity(73, false, ocean).lattice(0, 0), new ClassicNormalDensity(73, true, ocean).lattice(0, 0));
        ClassicNormalDensity.Biomes plains = (x, z) -> new ClassicNormalDensity.Profile(.125F, .05F);
        assertNotEquals(new ClassicNormalDensity(73, false, plains).lattice(0, 0)[8], new ClassicNormalDensity(73, true, plains).lattice(0, 0)[8]);
    }
}
