package ports.buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * A BuildCraft oil-family fluid. Loader backends may subclass it to attach loader-only data (Forge
 * fluid types); all behaviour lives here so both loaders flow identically.
 */
public class BCFluid extends FlowingFluid {
    private final BCFluidDefinition definition;
    private final boolean source;

    public BCFluid(BCFluidDefinition definition, boolean source) {
        this.definition = definition;
        this.source = source;
        if (!source) registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
    }

    public BCFluidDefinition definition() { return definition; }

    @Override public Fluid getFlowing() { return definition.flowing; }
    @Override public Fluid getSource() { return definition.source; }
    @Override public Item getBucket() { return definition.bucket(); }

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        super.createFluidStateDefinition(builder);
        if (!source) builder.add(LEVEL);
    }

    /** Oil-family fluids never form infinite sources, as in upstream. */
    @Override protected boolean canConvertToSource() { return false; }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override protected int getSlopeFindDistance(LevelReader level) { return 4; }
    @Override protected int getDropOff(LevelReader level) { return definition.dropOff(); }
    @Override public int getTickDelay(LevelReader level) { return definition.tickDelay(); }
    @Override protected float getExplosionResistance() { return 100.0F; }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !isSame(fluid);
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        LiquidBlock block = definition.block();
        return block.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override public boolean isSource(FluidState state) { return source; }
    @Override public int getAmount(FluidState state) { return source ? 8 : state.getValue(LEVEL); }
    @Override public boolean isSame(Fluid fluid) { return fluid == definition.source || fluid == definition.flowing; }
}
