package ports.jurassicraft.tourrail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Rendering-only block entity; shape lives in the block state and needs no extra NBT. */
public final class TourRailBlockEntity extends BlockEntity {
    public TourRailBlockEntity(BlockPos pos, BlockState state) { super(TourRailRegistry.RENDER_ENTITY.get(), pos, state); }
}
