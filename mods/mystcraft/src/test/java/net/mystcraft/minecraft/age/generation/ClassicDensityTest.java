package net.mystcraft.minecraft.age.generation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassicDensityTest {
    /** Independently extracted pinned classic initializeNoiseField + old bulk octave sampler. */
    @Test void preservesClassicDensityGoldenValuesAtNegativeCoordinates() {
        int[] indices = {0, 8, 16, 97, 212, 424};
        double[][] expected = {
            {649.7522440461612, -21.230691187524727, -10, 5.355959795754744, -13.546276388687946, -10},
            {-42.83619959134025, -40.475970505056125, -443.12530442074797, -222.92133995148538, -1.8188749843851468, -428.88468650843015}
        };
        for (int mode = 0; mode < 2; mode++) {
            double[] field = new ClassicDensity(123456789L, mode == 1).lattice(-1, -2);
            for (int i = 0; i < indices.length; i++) assertEquals(expected[mode][i], field[indices[i]], 1e-10);
        }
    }
    @Test void interpolationHitsLatticeCornersAndDoesNotChangeWithEvaluationOrder() {
        var generator = new ClassicDensity(0, false);
        double[] first = generator.lattice(-1, -2);
        generator.lattice(10000, -20000);
        assertArrayEquals(first, generator.lattice(-1, -2));
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) for (int y = 0; y < 16; y++)
            assertEquals(first[(x * 5 + z) * 17 + y], ClassicDensity.interpolate(first, x * 4, y * 8, z * 4), 0);
    }
}
