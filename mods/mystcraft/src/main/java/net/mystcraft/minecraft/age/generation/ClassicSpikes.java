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
final class ClassicSpikes extends ClassicWorldFeature {

	private BlockState state;

	public ClassicSpikes(Block block) {
		this(block.defaultBlockState());
	}

	public ClassicSpikes(BlockState state) {
		this.state = state;
	}

	@Override
	public boolean doGeneration(WorldGenLevel worldObj, RandomSource rand, BlockPos base) {
		if (worldObj.isEmptyBlock(base)) {
			int height = rand.nextInt(32) + 6;
			int width = rand.nextInt(4) + 1;

			for (int x = base.getX() - width; x <= base.getX() + width; ++x) {
				for (int z = base.getZ() - width; z <= base.getZ() + width; ++z) {
					int dx = x - base.getX();
					int dz = z - base.getZ();

					if (dx * dx + dz * dz <= width * width + 1 && worldObj.isEmptyBlock(new BlockPos(x, base.getY() - 1, z))) {
						return false;
					}
				}
			}

			for (int x = base.getX() - width; x <= base.getX() + width; ++x) {
				for (int z = base.getZ() - width; z <= base.getZ() + width; ++z) {
					int maxHeight = base.getY() + rand.nextInt(rand.nextInt(height) + 1) + 1;
					for (int y = base.getY(); y < maxHeight && y < 256; ++y) {
						int dx = x - base.getX();
						int dz = z - base.getZ();
						if (dx * dx + dz * dz <= width * width + 1) {
							placeBlock(worldObj, new BlockPos(x, y, z), state, 2);
						}
					}
				}
			}
			return true;
		}
		return false;
	}
}
