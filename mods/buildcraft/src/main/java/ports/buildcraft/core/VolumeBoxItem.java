package ports.buildcraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ports.buildcraft.core.marker.volume.ClientVolumeBoxes;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;

/** Upstream {@code ItemVolumeBox}: using it on a block makes a one-block volume box against the clicked face. */
public class VolumeBoxItem extends Item {
    public VolumeBoxItem(Properties properties) {
        super(properties);
    }

    /** Fails where a box already is. Clients predict from the boxes they were sent, so the arm swings. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos offset = context.getClickedPos().relative(context.getClickedFace());
        if (level.isClientSide) {
            for (VolumeBox volumeBox : ClientVolumeBoxes.VOLUME_BOXES) {
                if (volumeBox.box.contains(offset)) return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }
        VolumeBoxes volumeBoxes = VolumeBoxes.get((ServerLevel) level);
        if (volumeBoxes.getVolumeBoxAt(offset) != null) return InteractionResult.FAIL;
        volumeBoxes.addVolumeBox(offset);
        volumeBoxes.markDirty();
        return InteractionResult.CONSUME;
    }
}
