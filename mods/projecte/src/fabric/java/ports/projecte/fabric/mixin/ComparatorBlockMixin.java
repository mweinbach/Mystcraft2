/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeBlock;

/**
 * Comparators subscribe to weak redstone changes. {@code Level#updateNeighborsAt} tells every neighbor
 * {@code onNeighborChange} and only steps through a conductor when {@code getWeakChanges} is set, which is this block.
 */
@Mixin(ComparatorBlock.class)
public abstract class ComparatorBlockMixin implements IForgeBlock {

    @Override
    public boolean getWeakChanges(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader world, BlockPos pos, BlockPos neighbor) {
        if (pos.getY() == neighbor.getY() && world instanceof Level level && !level.isClientSide) {
            state.neighborChanged(level, pos, world.getBlockState(neighbor).getBlock(), neighbor, false);
        }
    }
}
