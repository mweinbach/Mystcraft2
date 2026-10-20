package ports.buildcraft.transport.pluggable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code PluggableBlocker}: the pipe plug, which stops the pipe connecting through its face. */
public class BlockerPluggable extends PipePluggable {
    private static final AABB[] BOXES = faceBoxes(2, 4, 4, 12);

    public BlockerPluggable(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
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
        return new ItemStack(BCTransport.PLUG_BLOCKER.get());
    }
}
