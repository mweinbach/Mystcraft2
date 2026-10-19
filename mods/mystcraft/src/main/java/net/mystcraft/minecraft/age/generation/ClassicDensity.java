package net.mystcraft.minecraft.age.generation;

import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Density equations from classic TerrainGenNether/End, without their unused noise arrays. */
final class ClassicDensity {
    private final ClassicOctaveNoise low, high, blend;
    private final boolean end;
    ClassicDensity(long seed, boolean end) {
        var random = new LegacyRandomSource(seed);
        low = new ClassicOctaveNoise(random, 16); high = new ClassicOctaveNoise(random, 16);
        blend = new ClassicOctaveNoise(random, 8); this.end = end;
    }
    /** Source storage layout is ((x * 5 + z) * 17 + y), spanning five-by-five-by-seventeen samples. */
    double[] lattice(int chunkX, int chunkZ) {
        double[] values = new double[5 * 5 * 17];
        double horizontal = end ? 1368.824 : 684.412, vertical = end ? 684.412 : 2053.236;
        double[] lower = low.field(chunkX * 4, 0, chunkZ * 4, 5, 17, 5, horizontal, vertical, horizontal);
        double[] upper = high.field(chunkX * 4, 0, chunkZ * 4, 5, 17, 5, horizontal, vertical, horizontal);
        double[] selection = blend.field(chunkX * 4, 0, chunkZ * 4, 5, 17, 5, horizontal / 80, vertical / (end ? 160 : 60), horizontal / 80);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 0; y < 17; y++) {
            double px = chunkX * 4.0 + x, pz = chunkZ * 4.0 + z;
            int index = (x * 5 + z) * 17 + y;
            double selector = (selection[index] / 10 + 1) / 2;
            double a = lower[index] / 512, b = upper[index] / 512;
            double density = selector < 0 ? a : selector > 1 ? b : a + (b - a) * selector;
            if (end) {
                float distance = 100 - (float) Math.sqrt((float) px * (float) px + (float) pz * (float) pz) * 4;
                distance = Math.max(-100, Math.min(80, distance));
                density += distance - 8;
                if (y > 6) {
                    double factor = Math.max(0, Math.min(1, (y - 6) / 64.0F));
                    density = density * (1 - factor) - 3000 * factor;
                }
                if (y < 8) {
                    double factor = (8 - y) / 7.0F;
                    density = density * (1 - factor) - 30 * factor;
                }
            } else {
                double shape = Math.cos(y * Math.PI * 6 / 17) * 2;
                double edge = y > 8 ? 16 - y : y;
                if (edge < 4) { edge = 4 - edge; shape -= edge * edge * edge * 10; }
                density -= shape;
                if (y > 13) { double factor = (y - 13) / 3.0F; density = density * (1 - factor) - 10 * factor; }
            }
            values[(x * 5 + z) * 17 + y] = density;
        }
        return values;
    }
    static double interpolate(double[] lattice, int x, int y, int z) {
        int cellX = x >> 2, cellZ = z >> 2, cellY = y >> 3;
        double tx = (x & 3) / 4.0, tz = (z & 3) / 4.0, ty = (y & 7) / 8.0;
        double a = lerp(ty, lattice[(cellX * 5 + cellZ) * 17 + cellY], lattice[(cellX * 5 + cellZ) * 17 + cellY + 1]);
        double b = lerp(ty, lattice[((cellX + 1) * 5 + cellZ) * 17 + cellY], lattice[((cellX + 1) * 5 + cellZ) * 17 + cellY + 1]);
        double c = lerp(ty, lattice[(cellX * 5 + cellZ + 1) * 17 + cellY], lattice[(cellX * 5 + cellZ + 1) * 17 + cellY + 1]);
        double d = lerp(ty, lattice[((cellX + 1) * 5 + cellZ + 1) * 17 + cellY], lattice[((cellX + 1) * 5 + cellZ + 1) * 17 + cellY + 1]);
        return lerp(tz, lerp(tx, a, b), lerp(tx, c, d));
    }
    private static double lerp(double delta, double a, double b) { return a + (b - a) * delta; }
}
