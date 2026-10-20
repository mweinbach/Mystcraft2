package ports.buildcraft.transport.pluggable;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code PluggablePowerAdaptor}: blocks the face like a plug but hands energy on that face
 * to the pipe's behaviour, so an engine or kinesis pipe can drive a wooden pipe without connecting.
 * The behaviour's port already converts loader energy units, so upstream's RF remainder is not kept.
 */
public class PowerAdaptorPluggable extends PipePluggable {
    private static final AABB[] BOXES = faceBoxes(0, 4, 3, 13);

    public PowerAdaptorPluggable(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
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

    @Nullable
    @Override
    public EnergyPort energyPort() {
        return holder.behaviour().energyPort(side);
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCTransport.PLUG_POWER_ADAPTOR.get());
    }
}
