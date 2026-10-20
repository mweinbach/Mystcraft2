/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import ports.extremereactors.corelib.block.multiblock.IMultiblockPartType;
import ports.extremereactors.corelib.block.multiblock.MultiblockPartBlock;
import ports.extremereactors.corelib.block.property.BlockFacingsProperty;
import ports.extremereactors.multiblock.IMultiblockController;

/**
 * A see-through machine part. Faces shared with another block of the same kind are not drawn.
 */
public class GlassBlock<Controller extends IMultiblockController<Controller>,
                        PartType extends Enum<PartType> & IMultiblockPartType>
        extends MultiblockPartBlock<Controller, PartType> {

    public GlassBlock(final MultiblockPartProperties<PartType> properties) {
        super(properties);
    }

    /**
     * Turn the given properties into the ones of a non-occluding, non-suffocating, see-through block.
     */
    public static Block.Properties addGlassProperties(final Block.Properties properties) {
        return properties
                .noOcclusion()
                .isValidSpawn((state, world, position, entityType) -> false)
                .isRedstoneConductor(GlassBlock::never)
                .isSuffocating(GlassBlock::never)
                .isViewBlocking(GlassBlock::never);
    }

    //region Block

    @Override
    public boolean skipRendering(final BlockState state, final BlockState adjacentState, final Direction direction) {
        return !(adjacentState.getBlock() instanceof INeverCauseRenderingSkip) &&
                (adjacentState.getBlock() == this || super.skipRendering(state, adjacentState, direction));
    }

    @Override
    public boolean propagatesSkylightDown(final BlockState state, final BlockGetter world, final BlockPos position) {
        return true;
    }

    @Override
    public float getShadeBrightness(final BlockState state, final BlockGetter world, final BlockPos position) {
        return 1.0f;
    }

    //endregion
    //region ModBlock

    @Override
    protected void buildBlockState(final StateDefinition.Builder<Block, BlockState> builder) {

        super.buildBlockState(builder);
        builder.add(BlockFacingsProperty.FACINGS);
    }

    @Override
    protected BlockState buildDefaultState(final BlockState state) {
        return super.buildDefaultState(state).setValue(BlockFacingsProperty.FACINGS, BlockFacingsProperty.None);
    }

    //endregion
    //region internals

    private static boolean never(final BlockState state, final BlockGetter world, final BlockPos position) {
        return false;
    }

    //endregion
}
