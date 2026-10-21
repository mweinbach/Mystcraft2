package ports.buildcraft.silicon.plug;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

/**
 * Upstream {@code FacadeBlockStateInfo}: one block state a facade can copy, the item that state costs, and the
 * properties that differ between states sharing that item.
 */
public final class FacadeBlockStateInfo {
    public final BlockState state;
    public final ItemStack requiredStack;
    public final Set<Property<?>> varyingProperties;
    public final boolean transparent;
    public final boolean visible;
    private final boolean[] sideSolid = new boolean[6];

    FacadeBlockStateInfo(BlockState state, ItemStack requiredStack, Set<Property<?>> varyingProperties) {
        this.state = state;
        this.requiredStack = requiredStack;
        this.varyingProperties = Set.copyOf(varyingProperties);
        this.transparent = !state.canOcclude();
        this.visible = !requiredStack.isEmpty();
        BlockGetter view = new SingleBlockView(state);
        for (Direction side : Direction.values()) {
            sideSolid[side.ordinal()] = state.isFaceSturdy(view, BlockPos.ZERO, side);
        }
    }

    public boolean isSideSolid(Direction side) {
        return sideSolid[side.ordinal()];
    }

    /** A view of one block at the origin, so solidity and the picked item do not depend on a world. */
    static final class SingleBlockView implements BlockGetter {
        private final BlockState state;

        SingleBlockView(BlockState state) {
            this.state = state;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 1;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }
    }
}
