package ports.buildcraft.silicon.plug;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream {@code PluggableTimer}. It has no state of its own: any timer on a pipe offers that pipe's gates the
 * short, medium and long timer triggers.
 */
public class PluggableTimer extends PipePluggable {
    private static final AABB[] BOXES = faceBoxes(2, 4, 5, 11);

    public PluggableTimer(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        super(definition, holder, side);
    }

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCSilicon.TIMER_ITEM.get());
    }
}
