package ports.buildcraft.transport.pluggable;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code PipePluggable}: something attached to one face of a pipe. Upstream's capability
 * lookups are the three port methods; a blocking pluggable hides the pipe's own ports on its face.
 */
public abstract class PipePluggable {
    public final PluggableDefinition definition;
    public final PipeBlockEntity holder;
    public final Direction side;

    protected PipePluggable(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        this.definition = definition;
        this.holder = holder;
        this.side = side;
    }

    public void save(CompoundTag tag) {}

    public void load(CompoundTag tag) {}

    /** Upstream {@code writeCreationPayload} and {@code writePayload}: the state clients need. */
    public void writeClient(CompoundTag tag) {}

    public void readClient(CompoundTag tag) {}

    /** Requests a client update; upstream {@code scheduleNetworkUpdate}. */
    public final void queueSync() {
        holder.queueSync();
    }

    public void onTick() {}

    /** The pluggable's box within the pipe's block, used for its shape and for picking it out of a hit. */
    public abstract AABB getBoundingBox();

    /** True if the pipe cannot connect outwards through this face. */
    public boolean isBlocking() {
        return false;
    }

    @Nullable
    public EnergyPort energyPort() {
        return null;
    }

    @Nullable
    public ItemPort itemPort() {
        return null;
    }

    @Nullable
    public FluidPort fluidPort() {
        return null;
    }

    /** Called whenever this pluggable is removed from the pipe. */
    public void onRemove() {}

    public void addDrops(List<ItemStack> drops) {
        ItemStack stack = getPickStack();
        if (!stack.isEmpty()) drops.add(stack);
    }

    public ItemStack getPickStack() {
        return ItemStack.EMPTY;
    }

    /** @param hit the clicked point, relative to the pipe's block */
    public InteractionResult onActivate(Player player, InteractionHand hand, Vec3 hit) {
        return InteractionResult.PASS;
    }

    public void onPlacedBy(Player player) {}

    /**
     * Upstream's per-face box tables, by {@code Direction} ordinal: each box spans {@code near} to
     * {@code far} pixels in from its face and {@code min} to {@code max} pixels across it.
     */
    protected static AABB[] faceBoxes(double near, double far, double min, double max) {
        AABB[] boxes = new AABB[6];
        double lo = min / 16, hi = max / 16;
        for (Direction face : Direction.values()) {
            boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            double start = (positive ? 16 - far : near) / 16, end = (positive ? 16 - near : far) / 16;
            boxes[face.ordinal()] = switch (face.getAxis()) {
                case X -> new AABB(start, lo, lo, end, hi, hi);
                case Y -> new AABB(lo, start, lo, hi, end, hi);
                case Z -> new AABB(lo, lo, start, hi, hi, end);
            };
        }
        return boxes;
    }
}
