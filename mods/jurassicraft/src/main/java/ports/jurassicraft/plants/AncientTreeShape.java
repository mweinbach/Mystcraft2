package ports.jurassicraft.plants;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The upstream generators' branch and canopy layouts, relative to the sapling. */
final class AncientTreeShape {
    private static final Direction[] HORIZONTAL = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
    record Placement(BlockState state, boolean airOnly) {}
    record Layout(int height, Map<BlockPos, Placement> blocks) {}

    private final Map<BlockPos, Placement> blocks = new LinkedHashMap<>();
    private final BlockState log;
    private final BlockState leaf;

    private AncientTreeShape(BlockState log, BlockState leaf) { this.log = log; this.leaf = leaf; }

    static Layout create(PlantSpecies species, RandomSource random, BlockState log, BlockState leaf) {
        AncientTreeShape shape = new AncientTreeShape(log, leaf);
        int height = switch (species) {
            case GINKGO -> shape.ginkgo(random);
            case CALAMITES -> shape.calamites(random);
            case PSARONIUS -> shape.psaronius(random);
            case PHOENIX -> shape.phoenix(random);
            case ARAUCARIA -> shape.araucaria(random);
            default -> throw new IllegalArgumentException("Not a tree: " + species);
        };
        return new Layout(height, shape.blocks);
    }

    private void put(BlockPos pos, BlockState state) { blocks.put(pos, new Placement(state, false)); }
    private void leaf(BlockPos pos) { put(pos, leaf); }
    private void trunk(int y) { put(BlockPos.ZERO.above(y), log); }
    private BlockState branch(Direction direction) { return log.setValue(RotatedPillarBlock.AXIS, direction.getAxis()); }

    private int ginkgo(RandomSource random) {
        int height = 4 + random.nextInt(16);
        for (int y = 0; y < height; y++) {
            BlockPos origin = BlockPos.ZERO.above(y);
            trunk(y);
            int length = Math.max(1, (height - y) / 3);
            if (y >= 2) {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    if (x != 0 || z != 0) leaf(origin.offset(x, 0, z));
                int size = (int) (length * 0.8);
                for (int x = -size; x <= size; x++) for (int z = -size; z <= size; z++)
                    if ((x != 0 || z != 0) && x * x + z * z < size * size) leaf(origin.offset(x, 0, z));
            }
            if (y % 3 == 2) for (Direction direction : HORIZONTAL) {
                BlockPos base = origin.relative(direction);
                BlockState horizontal = branch(direction);
                put(base, horizontal);
                leaf(base.above(2)); leaf(base.below());
                leaf(base.relative(direction.getClockWise(), 2)); leaf(base.relative(direction.getCounterClockWise(), 2));
                for (int i = 0; i < length; i++) {
                    BlockPos end = base.relative(direction, i + 1).above(i / 2 + 1);
                    put(end, horizontal);
                    leaf(end.above()); leaf(end.below());
                    leaf(end.relative(direction.getClockWise())); leaf(end.relative(direction.getCounterClockWise()));
                    if (i == length - 1) leaf(end.relative(direction));
                }
            }
        }
        BlockPos top = BlockPos.ZERO.above(height);
        leaf(top);
        for (Direction direction : HORIZONTAL) leaf(top.relative(direction));
        leaf(top.above());
        return height;
    }

    private int calamites(RandomSource random) {
        int height = 10 + random.nextInt(10), half = height / 2, branchIndex = 0;
        for (int y = 0; y < height; y++) {
            BlockPos origin = BlockPos.ZERO.above(y);
            trunk(y);
            boolean upper = y > half;
            branchIndex++;
            if (branchIndex > (upper ? 2 : 3)) branchIndex = 0;
            if (branchIndex >= (upper ? 2 : 3)) for (Direction direction : HORIZONTAL) {
                BlockPos base = origin.relative(direction);
                BlockState horizontal = branch(direction);
                put(base, horizontal); leaf(base.above());
                int length = Math.max(1, (upper ? y - half : (half - y) + half) / 2) + random.nextInt(2) - 1;
                for (int i = 0; i < length; i++) {
                    BlockPos end = base.relative(direction, i + 1).above(i / 2 + 1);
                    leaf(end);
                    if (!upper) {
                        if (i < length / 4 || height < 12) leaf(end.above());
                        if (i < length - 2) {
                            leaf(end.below()); leaf(end.relative(direction.getCounterClockWise())); leaf(end.relative(direction.getClockWise()));
                        } else leaf(end.above());
                    } else if (i >= length - 1) {
                        leaf(end.above()); leaf(end.above().relative(direction));
                    }
                }
                if (!upper) { put(base.relative(direction).above(), horizontal); leaf(base.relative(direction)); }
                else leaf(base.relative(direction).above(2));
            }
        }
        for (int i = 0; i < height / 4 + 1; i++) leaf(BlockPos.ZERO.above(height + i));
        return height;
    }

    private int psaronius(RandomSource random) {
        int scale = random.nextInt(1) + 1;
        int height = scale + 6 + random.nextInt(2), leafScale = scale + 2;
        for (int y = 0; y < height; y++) trunk(y);
        BlockPos top = BlockPos.ZERO.above(height);
        for (int i = -leafScale; i <= leafScale; i++) { leaf(top.offset(i, 0, 0)); leaf(top.offset(0, 0, i)); }
        for (Direction direction : HORIZONTAL) leaf(top.relative(direction, leafScale + 1).below());
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) leaf(top.offset(x, -1, z));
        clump(top, 2.5, 2.5);
        return height;
    }

    private int phoenix(RandomSource random) {
        int height = 7 + random.nextInt(6);
        for (int y = 0; y < height; y++) trunk(y);
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = -1; z <= 1; z++)
            leaf(BlockPos.ZERO.offset(x, height + y, z));
        BlockPos top = BlockPos.ZERO.above(height);
        for (int y = 2; y <= 4; y++) leaf(top.above(y));
        boolean longLowerFronds = random.nextBoolean();
        for (Direction direction : HORIZONTAL) {
            for (int d = 1; d <= 2; d++) leaf(top.above(2).relative(direction, d));
            for (int d = 2; d <= 3; d++) leaf(top.above(3).relative(direction, d));
            leaf(top.above(2).relative(direction, 4));
            leaf(top.relative(direction, 2));
            leaf(top.below().relative(direction, 3));
            leaf(top.below(2).relative(direction, 3));
            if (longLowerFronds) leaf(top.below(3).relative(direction, 3));
        }
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
            leaf(top.offset(x, 1, z)); leaf(top.offset(x, 2, z));
        }
        for (int x : new int[]{-3, 3}) for (int z : new int[]{-3, 3})
            for (int y = -1; y <= 1; y++) leaf(top.offset(x, y, z));
        return height;
    }

    private int araucaria(RandomSource random) {
        int height = 10 + random.nextInt(15), branchHeight = (int) (height / 1.3);
        for (int y = 0; y < height; y++) {
            BlockPos origin = BlockPos.ZERO.above(y);
            trunk(y);
            if (y > branchHeight) for (Direction direction : HORIZONTAL) {
                boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                if (y % 2 != (positive ? 0 : 1)) continue;
                BlockPos base = origin.relative(direction);
                put(base, branch(direction)); clump(base, 1.2, 1.2);
                if (y > branchHeight * 1.2) {
                    BlockPos tip = base.above().relative(direction);
                    put(tip, branch(direction)); clump(tip, 4, 4);
                }
            }
        }
        BlockPos top = BlockPos.ZERO.above(height);
        if (random.nextInt(10) == 0) { clump(top, 64, 6); clump(top, 8, 8); }
        else { clump(top, 32, 6); clump(top, 6, 6); }
        clump(top.above(2), 16, 3.5);
        return height;
    }

    /** Upstream clumps fill air only; size and sizeY are squared radii. */
    private void clump(BlockPos center, double size, double sizeY) {
        int radius = (int) Math.floor(Math.sqrt(size));
        int vertical = (int) Math.floor(Math.sqrt(sizeY));
        for (int x = -radius; x <= radius; x++) for (int y = -vertical; y <= vertical; y++) for (int z = -radius; z <= radius; z++)
            if (x * x + y * y * (size / sizeY) + z * z <= size)
                blocks.putIfAbsent(center.offset(x, y, z), new Placement(leaf, true));
    }
}
