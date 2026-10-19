package net.mystcraft.minecraft.age.generation;

import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Pinned TerrainGeneratorNormal density, including its float-weighted 5x5 biome blend. */
final class ClassicNormalDensity {
    record Profile(float height, float variation) {}
    @FunctionalInterface interface Biomes { Profile at(int quartX, int quartZ); }
    private final ClassicOctaveNoise low, high, blend, depth;
    private final boolean amplified;
    private final Biomes biomes;
    private final float[] weights = new float[25];
    ClassicNormalDensity(long seed, boolean amplified, Biomes biomes) {
        var random = new LegacyRandomSource(seed);
        low = new ClassicOctaveNoise(random, 16); high = new ClassicOctaveNoise(random, 16);
        blend = new ClassicOctaveNoise(random, 8);
        new ClassicOctaveNoise(random, 10); // Source constructs this unused generator before depth noise.
        depth = new ClassicOctaveNoise(random, 16);
        this.amplified = amplified; this.biomes = biomes;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            weights[(z + 2) * 5 + x + 2] = 10F / (float) Math.sqrt(x * x + z * z + 0.2F);
    }
    double[] lattice(int chunkX, int chunkZ) {
        int originX = chunkX * 4, originZ = chunkZ * 4;
        double[] lower = low.field(originX, 0, originZ, 5, 17, 5, 684.412, 684.412, 684.412);
        double[] upper = high.field(originX, 0, originZ, 5, 17, 5, 684.412, 684.412, 684.412);
        double[] selection = blend.field(originX, 0, originZ, 5, 17, 5, 684.412 / 80, 684.412 / 160, 684.412 / 80);
        double[] depths = depth.field(originX, 10, originZ, 5, 1, 5, 200, 1, 200);
        double[] result = new double[425];
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            Profile center = biomes.at(originX + x, originZ + z);
            float variationSum = 0, heightSum = 0, weightSum = 0;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                Profile nearby = biomes.at(originX + x + dx, originZ + z + dz);
                float height = nearby.height(), variation = nearby.variation();
                if (amplified && height > 0) { height = 1 + height * 2; variation = 1 + variation * 4; }
                float weight = weights[dx + 2 + (dz + 2) * 5] / (height + 2);
                if (height > center.height()) weight /= 2;
                variationSum += variation * weight; heightSum += height * weight; weightSum += weight;
            }
            variationSum /= weightSum; heightSum /= weightSum;
            variationSum = variationSum * 0.9F + 0.1F;
            heightSum = (heightSum * 4 - 1) / 8;
            double depthNoise = depths[x * 5 + z] / 8000;
            if (depthNoise < 0) depthNoise = -depthNoise * 0.3;
            depthNoise = depthNoise * 3 - 2;
            if (depthNoise < 0) { depthNoise /= 2; depthNoise = Math.max(-1, depthNoise); depthNoise /= 1.4; depthNoise /= 2; }
            else { depthNoise = Math.min(1, depthNoise); depthNoise /= 8; }
            for (int y = 0; y < 17; y++) {
                int index = (x * 5 + z) * 17 + y;
                double minHeight = heightSum; minHeight += depthNoise * 0.2; minHeight = minHeight * 17 / 16;
                double height = 17 / 2D + minHeight * 4;
                double average = ((y - height) * 12 * 128) / 128 / variationSum;
                if (average < 0) average *= 4;
                double selector = (selection[index] / 10 + 1) / 2;
                double a = lower[index] / 512, b = upper[index] / 512;
                double density = selector < 0 ? a : selector > 1 ? b : a + (b - a) * selector;
                density -= average;
                if (y > 13) { double factor = (y - 13) / 3F; density = density * (1 - factor) - 10 * factor; }
                result[index] = density;
            }
        }
        return result;
    }
}
