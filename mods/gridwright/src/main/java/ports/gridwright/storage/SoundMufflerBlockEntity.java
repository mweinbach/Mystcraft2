package ports.gridwright.storage;

import ports.gridwright.registry.ModUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A sound muffler's presence. It has no data; it only tells {@link SoundMuffling} where it is while it
 * is loaded, so a sound never has to search the blocks around it.
 */
public class SoundMufflerBlockEntity extends BlockEntity {

    public SoundMufflerBlockEntity(BlockPos pos, BlockState state) {
        super(ModUtilities.SOUND_MUFFLER_ENTITY.get(), pos, state);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null) SoundMuffling.add(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) SoundMuffling.remove(level, worldPosition);
    }
}
