package ports.buildcraft.transport.behaviour;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.ItemFluids;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;

/**
 * Upstream {@code PipeBehaviourDiamondFluid}: the diamond item pipe's nine phantom filter slots per
 * face, read as fluid containers. A face whose filters contain the travelling fluid is preferred;
 * a face whose filters hold other fluids rejects it. Faces with no fluid filters are unaffected.
 */
public class DiamondFluidBehaviour extends DiamondItemBehaviour {
    public DiamondFluidBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    public void fluidSideCheck(SideCheck check) {
        Fluid toCompare = check.fluid;
        if (toCompare == null) return;
        for (Direction face : Direction.values()) {
            if (!check.isAllowed(face) || !pipe.isConnected(face)) continue;
            int offset = FILTERS_PER_SIDE * face.ordinal();
            boolean sideAllowed = false;
            boolean foundItem = false;
            for (int i = 0; i < FILTERS_PER_SIDE; i++) {
                ItemStack compareTo = filters.getItem(offset + i);
                if (compareTo.isEmpty()) continue;
                Fluid contained = containedFluid(compareTo);
                if (contained == null) continue;
                foundItem = true;
                if (contained.isSame(toCompare)) {
                    sideAllowed = true;
                    break;
                }
            }
            if (foundItem) {
                if (sideAllowed) check.increasePriority(face, 12);
                else check.disallow(face);
            }
        }
    }

    /** Upstream {@code FluidUtil.getFluidContained}: null for a container with nothing in it. */
    @Nullable
    private static Fluid containedFluid(ItemStack stack) {
        ItemFluids fluids = Platform.get().itemFluids(stack);
        if (fluids == null) return null;
        FluidAmount contained = fluids.drain(Integer.MAX_VALUE, true);
        return contained.isEmpty() ? null : contained.fluid();
    }
}
