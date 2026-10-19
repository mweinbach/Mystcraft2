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
final class ClassicCrystalFormation extends ClassicWorldFeature {

	static final byte otherCoordPairs[] = { 2, 0, 0, 1, 2, 1 };
	private WorldGenLevel worldObj;
	private BlockState block;
	private BlockPos crystalPos;

	public ClassicCrystalFormation(Block block) {
		this(block.defaultBlockState());
	}

	public ClassicCrystalFormation(BlockState state) {
		this.block = state;
	}

	private void generateLine(RandomSource rand) {
		float angle1 = (rand.nextFloat() * 140.0F + 15.0F);
		int length = rand.nextInt(7) + 6;
		int[] end = new int[] {
				crystalPos.getX() + (int) (length * Math.cos(angle1 * Math.PI / 180)),
				crystalPos.getY() + (int) (length * Math.sin(angle1 * Math.PI / 180)),
				crystalPos.getZ() + rand.nextInt(7) - 3
		};
		int[] start = new int[] {
				crystalPos.getX(),
				crystalPos.getY(),
				crystalPos.getZ()
		};
		placeBlockLine(start, end, block);
	}

	private void placeBlockLine(int ai[], int ai1[], BlockState state) {
		int ai2[] = { 0, 0, 0 };
		byte byte0 = 0;
		int j = 0;
		for (; byte0 < 3; ++byte0) {
			ai2[byte0] = ai1[byte0] - ai[byte0];
			if (Math.abs(ai2[byte0]) > Math.abs(ai2[j])) {
				j = byte0;
			}
		}

		if (ai2[j] == 0) {
			return;
		}
		byte byte1 = otherCoordPairs[j];
		byte byte2 = otherCoordPairs[j + 3];
		byte byte3;
		if (ai2[j] > 0) {
			byte3 = 1;
		} else {
			byte3 = -1;
		}
		double d = (double) ai2[byte1] / (double) ai2[j];
		double d1 = (double) ai2[byte2] / (double) ai2[j];
		int ai3[] = { 0, 0, 0 };
		int k = 0;
		for (int l = ai2[j] + byte3; k != l; k += byte3) {
			ai3[j] = Mth.floor((ai[j] + k) + 0.5D);
			ai3[byte1] = Mth.floor(ai[byte1] + k * d + 0.5D);
			ai3[byte2] = Mth.floor(ai[byte2] + k * d1 + 0.5D);
			drawSphere(new BlockPos(ai3[0], ai3[1], ai3[2]), state);// setBlockAndMetadata(worldObj, ai3[0], ai3[1], ai3[2], i, 0);
		}
	}

	private void drawSphere(BlockPos pos, BlockState state) {
		setBlock(pos, state);
		for (Direction face : Direction.values()) {
			setBlock(pos.relative(face), state);
		}
	}

	private void setBlock(BlockPos pos, BlockState state) {
		if (!worldObj.getBlockState(pos).getBlock().equals(Blocks.BEDROCK)) {
            DeferredCrystalWrites.place(worldObj, pos, state, 3);
		}
	}

	@Override
	public boolean doGeneration(WorldGenLevel world, RandomSource rand, BlockPos pos) {
		worldObj = world;
		crystalPos = new BlockPos(pos.getX(), 0, pos.getZ());

		if (!validLocation())
			return false;
		int count = rand.nextInt(3) + 1;
		for (int i1 = 0; i1 < count; ++i1) {
			generateLine(rand);
		}

		return true;
	}

	private boolean validLocation() {
		BlockState state = worldObj.getBlockState(crystalPos);
		while (state.getMaterial() != Material.AIR) {
			crystalPos = crystalPos.above();
			if (crystalPos.getY() > 256) {
				return false;
			}
			state = worldObj.getBlockState(crystalPos);
		}
		while (!state.getMaterial().isLiquid() && state.getMaterial() != Material.AIR) {
			crystalPos = crystalPos.above();
		}
		crystalPos = crystalPos.below(2);
		return worldObj.getBlockState(crystalPos).getMaterial() != Material.AIR;
	}
}
