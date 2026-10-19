package net.mystcraft.minecraft.age.generation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/** Pinned Mystcraft-Legacy source algorithm, LGPL-3.0; modern world/registry adapter. */
final class ClassicLakes extends ClassicWorldFeature {

	private BlockState state;

	public ClassicLakes(BlockState state) {
		this.state = state;
	}

	public ClassicLakes(Block block) {
		this(block.defaultBlockState());
	}

	@Override
	public boolean doGeneration(WorldGenLevel worldObj, RandomSource rand, BlockPos pos) {
		pos = pos.below(8);

		for (pos = pos.offset(0, 0, -8); pos.getY() > 5 && worldObj.isEmptyBlock(pos); pos = pos.below()) {
			;
		}

		if (pos.getY() <= 4) {
			return false;
		}
		pos = pos.below(4);
		boolean[] aboolean = new boolean[2048];
		int l = rand.nextInt(4) + 4;
		int i1;

		for (i1 = 0; i1 < l; ++i1) {
			double d0 = rand.nextDouble() * 6.0D + 3.0D;
			double d1 = rand.nextDouble() * 4.0D + 2.0D;
			double d2 = rand.nextDouble() * 6.0D + 3.0D;
			double d3 = rand.nextDouble() * (16.0D - d0 - 2.0D) + 1.0D + d0 / 2.0D;
			double d4 = rand.nextDouble() * (8.0D - d1 - 4.0D) + 2.0D + d1 / 2.0D;
			double d5 = rand.nextDouble() * (16.0D - d2 - 2.0D) + 1.0D + d2 / 2.0D;

			for (int j1 = 1; j1 < 15; ++j1) {
				for (int k1 = 1; k1 < 15; ++k1) {
					for (int l1 = 1; l1 < 7; ++l1) {
						double d6 = (j1 - d3) / (d0 / 2.0D);
						double d7 = (l1 - d4) / (d1 / 2.0D);
						double d8 = (k1 - d5) / (d2 / 2.0D);
						double d9 = d6 * d6 + d7 * d7 + d8 * d8;

						if (d9 < 1.0D) {
							aboolean[(j1 * 16 + k1) * 8 + l1] = true;
						}
					}
				}
			}
		}

		int i2;
		int j2;
		boolean flag;

		for (i1 = 0; i1 < 16; ++i1) {
			for (j2 = 0; j2 < 16; ++j2) {
				for (i2 = 0; i2 < 8; ++i2) {
					flag = !aboolean[(i1 * 16 + j2) * 8 + i2] && (i1 < 15 && aboolean[((i1 + 1) * 16 + j2) * 8 + i2] || i1 > 0 && aboolean[((i1 - 1) * 16 + j2) * 8 + i2] || j2 < 15 && aboolean[(i1 * 16 + j2 + 1) * 8 + i2] || j2 > 0 && aboolean[(i1 * 16 + (j2 - 1)) * 8 + i2] || i2 < 7 && aboolean[(i1 * 16 + j2) * 8 + i2 + 1] || i2 > 0 && aboolean[(i1 * 16 + j2) * 8 + (i2 - 1)]);

					if (flag) {
						BlockPos posAt = pos.offset(i1, i2, j2);
						Material material = worldObj.getBlockState(posAt).getMaterial();

						if (i2 >= 4 && material.isLiquid()) {
							return false;
						}

						if (i2 < 4 && !material.isSolid() && !worldObj.getBlockState(posAt).equals(state)) {
							return false;
						}
					}
				}
			}
		}

		for (i1 = 0; i1 < 16; ++i1) {
			for (j2 = 0; j2 < 16; ++j2) {
				for (i2 = 0; i2 < 8; ++i2) {
					if (aboolean[(i1 * 16 + j2) * 8 + i2]) {
						placeBlock(worldObj, pos.offset(i1, i2, j2), i2 >= 4 ? Blocks.AIR.defaultBlockState() : this.state, 2);
					}
				}
			}
		}

		return true;
	}
}
