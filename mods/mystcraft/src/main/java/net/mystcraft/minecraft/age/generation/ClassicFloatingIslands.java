package net.mystcraft.minecraft.age.generation;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Mechanical pinned MapGenFloatingIslands geometry; modern quart-biome storage adapter. */
public final class ClassicFloatingIslands {
    private final long seed;
    private final BlockState replacement;
    private final Holder<Biome> biome;
    private final Random rand = new Random();
    private final ClassicOctaveNoise noise;
    private final int range = 5, rate = 192;
    private boolean[] allModified;
    public ClassicFloatingIslands(long seed, BlockState replacement, Holder<Biome> biome) {
        this.seed = seed; this.replacement = java.util.Objects.requireNonNull(replacement);
        this.biome = java.util.Objects.requireNonNull(biome);
        // Pinned source constructed this from unseeded MapGenAdvanced.rand; freeze it to the symbol seed.
        noise = new ClassicOctaveNoise(new LegacyRandomSource(seed), 4);
    }
    public synchronized boolean[] generate(ChunkAccess chunk) {
        allModified = new boolean[256];
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        rand.setSeed(seed); long xs = rand.nextLong(), zs = rand.nextLong();
        for (int x = cx - range; x <= cx + range; x++) for (int z = cz - range; z <= cz + range; z++) {
            rand.setSeed(x * xs ^ z * zs ^ seed);
            recursiveGenerate(x, z, cx, cz, chunk);
        }
        applyBiomes(chunk);
        return allModified.clone();
    }
	protected void generateCaveNode(long seed, int chunkX, int chunkZ, ChunkAccess primer, boolean[] modified, double baseX, double baseY, double baseZ, float scalar, float angleB, float angleC, int loopc, int maxLoops, double squash) {
		double chunkXmid = chunkX * 16 + 8;
		double chunkZmid = chunkZ * 16 + 8;
		int layers = 256;
		float f = 0.0F;
		float f1 = 0.0F;
		Random random = new Random(seed);

		if (maxLoops <= 0) {
			int i = range * 16 - 16;
			maxLoops = i - random.nextInt(i / 4);
		}

		boolean flag = false;

		if (loopc == -1) {
			loopc = maxLoops / 2;
			flag = true;
		}

		int j = random.nextInt(maxLoops / 2) + maxLoops / 4;
		boolean flag1 = random.nextInt(6) == 0;

		for (; loopc < maxLoops; ++loopc) {
			double d2 = 1.5D + (Mth.sin((loopc * (float) Math.PI) / maxLoops) * scalar * 1.0F);
			double d3 = d2 * squash;
			float f2 = Mth.cos(angleC);
			float f3 = Mth.sin(angleC);
			baseX += Mth.cos(angleB) * f2;
			baseY += f3;
			baseZ += Mth.sin(angleB) * f2;

			if (flag1) {
				angleC *= 0.92F;
			} else {
				angleC *= 0.7F;
			}

			angleC += f1 * 0.1F;
			angleB += f * 0.1F;
			f1 *= 0.9F;
			f *= 0.75F;
			f1 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 1.0F;
			f += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4F;

			if (!flag && loopc == j && scalar > 1.0F && maxLoops > 0) {
				generateCaveNode(random.nextLong(), chunkX, chunkZ, primer, modified, baseX, baseY, baseZ, random.nextFloat() * 0.5F + 0.5F, angleB - ((float) Math.PI / 2F), angleC / 3F, loopc, maxLoops, squash);
				generateCaveNode(random.nextLong(), chunkX, chunkZ, primer, modified, baseX, baseY, baseZ, random.nextFloat() * 0.5F + 0.5F, angleB + ((float) Math.PI / 2F), angleC / 3F, loopc, maxLoops, squash);
				return;
			}

			if (!flag && random.nextInt(4) == 0) {
				continue;
			}

			double xoffset = baseX - chunkXmid;
			double yoffset = baseZ - chunkZmid;
			double zoffset = maxLoops - loopc;
			double d7 = scalar + 2.0F + 16F;

			if ((xoffset * xoffset + yoffset * yoffset) - zoffset * zoffset > d7 * d7) {
				return;
			}

			if (baseX < chunkXmid - 16D - d2 * 2D || baseZ < chunkZmid - 16D - d2 * 2D || baseX > chunkXmid + 16D + d2 * 2D || baseZ > chunkZmid + 16D + d2 * 2D) {
				continue;
			}

			int minX = Mth.floor(baseX - d2) - chunkX * 16 - 1;
			int maxX = (Mth.floor(baseX + d2) - chunkX * 16) + 1;
			int minY = Mth.floor(baseY - d3) - 1;
			int maxY = Mth.floor(baseY + d3) + 1;
			int minZ = Mth.floor(baseZ - d2) - chunkZ * 16 - 1;
			int maxZ = (Mth.floor(baseZ + d2) - chunkZ * 16) + 1;

			if (minX < 0) {
				minX = 0;
			}

			if (maxX > 16) {
				maxX = 16;
			}

			if (minY < 1) {
				minY = 1;
			}

			if (maxY > layers) {
				maxY = layers;
			}

			if (minZ < 0) {
				minZ = 0;
			}

			if (maxZ > 16) {
				maxZ = 16;
			}

			for (int localY = minY; localY < maxY; ++localY) {
				double yfactor = ((localY + 0.5D) - baseY) / d3;
				double yfactorSq = yfactor * yfactor;
				for (int localZ = minZ; localZ < maxZ; ++localZ) {
					double zfactor = (((localZ + chunkZ * 16) + 0.5D) - baseZ) / d2;
					double zfactorSq = zfactor * zfactor;
					for (int localX = minX; localX < maxX; ++localX) {
						double xfactor = (((localX + chunkX * 16) + 0.5D) - baseX) / d2;
						double xfactorSq = xfactor * xfactor;

						int coords = localY << 8 | localZ << 4 | localX;

						double total = xfactorSq + yfactorSq + zfactorSq;
						if (total < 1.0D) {
							placeBlock(primer, modified, coords, localX, localY, localZ);
						}
					}
				}
			}

			if (flag) {
				break;
			}
		}
	}

    private void placeBlock(ChunkAccess chunk, boolean[] modified, int coords, int x, int y, int z) {
        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
        if (chunk.isOutsideBuildHeight(pos)) return;
        BlockState current = chunk.getBlockState(pos);
        if (current.is(Blocks.BEDROCK) || (!replacement.getMaterial().isSolid() && current.getMaterial().isLiquid())) return;
        chunk.setBlockState(pos, replacement, false); modified[coords & 255] = true;
    }
    private void replaceBlocksForBiome(int cx, int cz, ChunkAccess chunk, boolean[] modified, Holder<Biome> ignored) {
        double[] stoneNoise = noise.field(cx * 16, cz * 16, 0, 16, 16, 1, 0.0625, 0.0625, 0.0625);
        String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        BlockState top = Blocks.GRASS_BLOCK.defaultBlockState(), filler = Blocks.DIRT.defaultBlockState();
        if (name.contains("desert") || name.equals("beach") || name.equals("snowy_beach")) top = filler = Blocks.SAND.defaultBlockState();
        else if (name.contains("badlands")) top = filler = Blocks.RED_SAND.defaultBlockState();
        else if (name.equals("mushroom_fields")) top = Blocks.MYCELIUM.defaultBlockState();
        else if (name.equals("stony_shore")) top = filler = Blocks.STONE.defaultBlockState();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (!modified[x + z * 16]) continue;
            int thickness = (int) (stoneNoise[z + x * 16] / 3 + 3 + rand.nextDouble() * 0.25), counter = -1;
            BlockState under = filler;
            for (int y = 255; y >= 0; y--) {
                BlockPos pos = new BlockPos(cx * 16 + x, y, cz * 16 + z);
                if (chunk.isOutsideBuildHeight(pos) || !chunk.getBlockState(pos).is(Blocks.STONE)) continue;
                // Pinned source's IBlockState-vs-Block comparison is repaired to the intended stone test.
                if (counter == -1) { under = filler; counter = thickness; chunk.setBlockState(pos, top, false); continue; }
                if (counter <= 0) continue;
                --counter; chunk.setBlockState(pos, under, false);
                if (counter == 0 && under.is(Blocks.SAND)) { counter = rand.nextInt(4); under = Blocks.SANDSTONE.defaultBlockState(); }
            }
        }
    }
    private void applyBiomes(ChunkAccess chunk) {
        boolean[] quart = new boolean[16];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) if (allModified[x + z * 16]) quart[(x >> 2) + (z >> 2) * 4] = true;
        boolean any = false; for (boolean value : quart) any |= value;
        if (!any) return;
        for (var section : chunk.getSections()) {
            var previous = new java.util.ArrayList<Holder<Biome>>(64);
            for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) previous.add(section.getNoiseBiome(x, y, z));
            section.fillBiomesFromNoise((x, y, z, sampler) -> quart[(x & 3) + (z & 3) * 4] ? biome
                    : previous.get((y & 3) * 16 + (z & 3) * 4 + (x & 3)), Climate.empty(), chunk.getPos().x * 4, chunk.getPos().z * 4);
        }
    }
	protected void recursiveGenerate(int x, int z, int chunkX, int chunkZ, ChunkAccess primer) {
		if (rand.nextInt(rate) != 0) {
			return;
		}
		boolean[] modified = new boolean[256];

		double dx = x * 16 + rand.nextInt(16);
		double dy = rand.nextInt(rand.nextInt(50) + 50) + 150;
		double dz = z * 16 + rand.nextInt(16);
		// generateLargeCaveNode(rand.nextLong(), chunkX, chunkZ, blocks, metadata, dx, dy, dz);
		generateCaveNode(rand.nextLong(), chunkX, chunkZ, primer, modified, dx, dy, dz, 12F, 0.0F, 0.0F, -1, -1, 0.2D);
		int subelements = rand.nextInt(12) + 40;
		for (int i = 0; i < subelements; ++i) {
			double subx = dx + (rand.nextDouble() - rand.nextDouble()) * 20.0D;
			double suby = dy + (rand.nextDouble() - rand.nextDouble()) * 10.0D;
			double subz = dz + (rand.nextDouble() - rand.nextDouble()) * 20.0D;
			float scale = rand.nextFloat() * 3F + 1F;
			generateCaveNode(rand.nextLong(), chunkX, chunkZ, primer, modified, subx, suby, subz, scale, 0.0F, 0.0F, -1, -1, 0.4D);
		}
		replaceBlocksForBiome(chunkX, chunkZ, primer, modified, biome);
		for (int i = 0; i < 256; i++) allModified[i] |= modified[i];
	}
}
