package net.mystcraft.minecraft.age.generation;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Classic ChunkProviderMyst four-level simplex region, with source octave scales/amplitudes. */
final class ClassicSurfaceNoise {
    private final SimplexNoise[] levels = new SimplexNoise[4];
    ClassicSurfaceNoise(long seed) {
        var random = new LegacyRandomSource(seed);
        for (int i = 0; i < levels.length; i++) levels[i] = new SimplexNoise(random);
    }
    double[] field(int blockX, int blockZ) {
        double[] result = new double[256]; double frequency = 1;
        for (SimplexNoise level : levels) {
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                result[z * 16 + x] += level.getValue((blockX + x) * 0.0625 * frequency + level.xo,
                        (blockZ + z) * 0.0625 * frequency + level.yo) * (0.55 / frequency);
            frequency *= 0.5;
        }
        return result;
    }
}
