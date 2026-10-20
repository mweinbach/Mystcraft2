package ports.buildcraft.builders.snapshot;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Fluids2;

/**
 * Upstream {@code SchematicBlockFluid}: source blocks cost a bucket and are placed once the blocks beside and
 * below them are; flowing blocks cost nothing and count as built. Blocks that only hold water by being
 * waterlogged keep their own schematic.
 */
public class SchematicBlockFluid implements SchematicBlock {
    private BlockState blockState;
    private boolean isFlowing;

    public static boolean predicate(SchematicBlockContext context) {
        return context.block() instanceof LiquidBlock && !context.blockState().getFluidState().isEmpty();
    }

    @Override
    public void init(SchematicBlockContext context) {
        blockState = context.blockState();
        isFlowing = !context.blockState().getFluidState().isSource();
    }

    @Override
    public Set<BlockPos> getRequiredBlockOffsets() {
        return Stream.concat(Direction.Plane.HORIZONTAL.stream(), Stream.of(Direction.DOWN))
                .map(Direction::getNormal).map(BlockPos::new).collect(Collectors.toSet());
    }

    @Override
    public List<FluidAmount> computeRequiredFluids() {
        FluidState fluid = blockState.getFluidState();
        return fluid.isEmpty() ? List.of() : List.of(new FluidAmount(Fluids2.still(fluid.getType()), 1000));
    }

    @Override
    public SchematicBlockFluid getRotated(Rotation rotation) {
        SchematicBlockFluid schematicBlock = SchematicBlockManager.createCleanCopy(this);
        schematicBlock.blockState = blockState;
        schematicBlock.isFlowing = isFlowing;
        return schematicBlock;
    }

    @Override
    public boolean canBuild(Level level, BlockPos pos) {
        return level.isEmptyBlock(pos)
                || BlockUtil.fluidWithFlowing(level, pos) == Fluids2.still(blockState.getFluidState().getType()) && !level.getFluidState(pos).isSource();
    }

    @Override
    public boolean build(Level level, BlockPos pos) {
        if (isFlowing) return true;
        if (level.setBlock(pos, blockState, 11)) {
            Stream.concat(Stream.of(Direction.values()).map(Direction::getNormal).map(BlockPos::new), Stream.of(BlockPos.ZERO))
                    .map(pos::offset)
                    .forEach(updatePos -> level.updateNeighborsAt(updatePos, blockState.getBlock()));
            return true;
        }
        return false;
    }

    @Override
    public boolean buildWithoutChecks(Level level, BlockPos pos) {
        return level.setBlock(pos, blockState, 0);
    }

    @Override
    public boolean isBuilt(Level level, BlockPos pos) {
        return isFlowing || blockState.equals(level.getBlockState(pos));
    }

    @Override
    public BlockState getPreviewState() {
        return blockState;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("blockState", NbtUtils.writeBlockState(blockState));
        tag.putBoolean("isFlowing", isFlowing);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        blockState = NbtUtils.readBlockState(tag.getCompound("blockState"));
        isFlowing = tag.getBoolean("isFlowing");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchematicBlockFluid that = (SchematicBlockFluid) o;
        return isFlowing == that.isFlowing && blockState.equals(that.blockState);
    }

    @Override
    public int hashCode() {
        return 31 * blockState.hashCode() + (isFlowing ? 1 : 0);
    }
}
