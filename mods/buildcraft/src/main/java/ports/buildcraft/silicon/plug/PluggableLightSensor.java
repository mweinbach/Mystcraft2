package ports.buildcraft.silicon.plug;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream {@code PluggableLightSensor}. It has no state of its own: a gate on the same face is offered the bright
 * and dark light triggers, which read the block just outside the sensor.
 */
public class PluggableLightSensor extends PipePluggable {
    private static final AABB[] BOXES = faceBoxes(2, 4, 5, 11);

    public PluggableLightSensor(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
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
        return new ItemStack(BCSilicon.LIGHT_SENSOR_ITEM.get());
    }
}
