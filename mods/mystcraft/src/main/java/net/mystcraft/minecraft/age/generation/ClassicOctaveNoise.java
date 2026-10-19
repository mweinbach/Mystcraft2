package net.mystcraft.minecraft.age.generation;

import net.minecraft.util.RandomSource;

/**
 * Classic octave/permutation math. The historical bulk sampler caches corner gradients until
 * the integer Y cell changes; pointwise modern ImprovedNoise does not preserve that behavior.
 */
final class ClassicOctaveNoise {
    private static final int[] GX = {1,-1,1,-1,1,-1,1,-1,0,0,0,0,1,0,-1,0};
    private static final int[] GY = {1,1,-1,-1,0,0,0,0,1,-1,1,-1,1,-1,1,-1};
    private static final int[] GZ = {0,0,0,0,1,1,-1,-1,1,1,-1,-1,0,1,0,-1};
    private final Octave[] octaves;
    ClassicOctaveNoise(RandomSource random, int count) {
        octaves = new Octave[count];
        for (int i = 0; i < count; i++) octaves[i] = new Octave(random);
    }
    double[] field(int x, int y, int z, int width, int height, int depth, double sx, double sy, double sz) {
        if (height < 1) throw new IllegalArgumentException("Empty classic noise field");
        double[] result = new double[width * height * depth];
        double frequency = 1;
        for (Octave octave : octaves) {
            octave.add(result, wrap(x * frequency * sx), y * frequency * sy, wrap(z * frequency * sz),
                    width, height, depth, sx * frequency, sy * frequency, sz * frequency, 1 / frequency);
            frequency /= 2;
        }
        return result;
    }
    private static double wrap(double value) { long integer = (long) Math.floor(value); return value - integer + integer % 16777216L; }
    private static double ease(double value) { return value * value * value * (value * (value * 6 - 15) + 10); }
    private static double mix(double t, double a, double b) { return a + t * (b - a); }
    private static double gradient(int hash, double x, double y, double z) {
        int index = hash & 15; return GX[index] * x + GY[index] * y + GZ[index] * z;
    }
    private static final class Octave {
        private final int[] permutation = new int[512];
        private final double offsetX, offsetY, offsetZ;
        Octave(RandomSource random) {
            offsetX = random.nextDouble() * 256; offsetY = random.nextDouble() * 256; offsetZ = random.nextDouble() * 256;
            for (int i = 0; i < 256; i++) permutation[i] = i;
            for (int i = 0; i < 256; i++) {
                int swap = i + random.nextInt(256 - i), previous = permutation[i];
                permutation[i] = permutation[swap]; permutation[swap] = previous;
                permutation[i + 256] = permutation[i];
            }
        }
        private void add2D(double[] result, double ox, double oz, int width, int depth, double sx, double sz, double amplitude) {
            int index = 0;
            for (int x = 0; x < width; x++) {
                double px = ox + x * sx + offsetX;
                int ix = (int) Math.floor(px) & 255; px -= Math.floor(px);
                double tx = ease(px);
                for (int z = 0; z < depth; z++) {
                    double pz = oz + z * sz + offsetZ;
                    int iz = (int) Math.floor(pz) & 255; pz -= Math.floor(pz);
                    int left = permutation[permutation[ix]] + iz, right = permutation[permutation[ix + 1]] + iz;
                    double front = mix(tx, gradient(permutation[left], px, 0, pz), gradient(permutation[right], px - 1, 0, pz));
                    double back = mix(tx, gradient(permutation[left + 1], px, 0, pz - 1), gradient(permutation[right + 1], px - 1, 0, pz - 1));
                    result[index++] += mix(ease(pz), front, back) * amplitude;
                }
            }
        }
        void add(double[] result, double ox, double oy, double oz, int width, int height, int depth,
                 double sx, double sy, double sz, double amplitude) {
            if (height == 1) { add2D(result, ox, oz, width, depth, sx, sz, amplitude); return; }
            int index = 0;
            for (int x = 0; x < width; x++) {
                double px = ox + x * sx + offsetX;
                int ix = (int) Math.floor(px) & 255; px -= Math.floor(px);
                double tx = ease(px);
                for (int z = 0; z < depth; z++) {
                    double pz = oz + z * sz + offsetZ;
                    int iz = (int) Math.floor(pz) & 255; pz -= Math.floor(pz);
                    double tz = ease(pz), frontBottom = 0, frontTop = 0, backBottom = 0, backTop = 0;
                    int previousCell = -1;
                    for (int y = 0; y < height; y++) {
                        double py = oy + y * sy + offsetY;
                        int iy = (int) Math.floor(py) & 255; py -= Math.floor(py);
                        if (y == 0 || iy != previousCell) {
                            previousCell = iy;
                            int left = permutation[ix] + iy, right = permutation[ix + 1] + iy;
                            int lf = permutation[left] + iz, lt = permutation[left + 1] + iz;
                            int rf = permutation[right] + iz, rt = permutation[right + 1] + iz;
                            frontBottom = mix(tx, gradient(permutation[lf], px, py, pz), gradient(permutation[rf], px - 1, py, pz));
                            frontTop = mix(tx, gradient(permutation[lt], px, py - 1, pz), gradient(permutation[rt], px - 1, py - 1, pz));
                            backBottom = mix(tx, gradient(permutation[lf + 1], px, py, pz - 1), gradient(permutation[rf + 1], px - 1, py, pz - 1));
                            backTop = mix(tx, gradient(permutation[lt + 1], px, py - 1, pz - 1), gradient(permutation[rt + 1], px - 1, py - 1, pz - 1));
                        }
                        double ty = ease(py);
                        result[index++] += mix(tz, mix(ty, frontBottom, frontTop), mix(ty, backBottom, backTop)) * amplitude;
                    }
                }
            }
        }
    }
}
