package ports.buildcraft.transport.flow;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code PipeFlow}: what a pipe carries (items, fluids or power) and how it moves. */
public abstract class PipeFlow {
    protected final PipeBlockEntity pipe;

    protected PipeFlow(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    public abstract boolean canConnect(Direction face, PipeFlow other);

    public abstract boolean canConnectTile(Direction face, BlockEntity tile);

    public void serverTick() {}

    public void clientTick() {}

    public void addDrops(List<ItemStack> drops) {}

    /** Upstream {@code PipeEventStatement.AddTriggerInternal}, as fired to the flow. */
    public void addInternalTriggers(Collection<Trigger.Internal> triggers) {}

    public abstract void save(CompoundTag tag);

    public abstract void load(CompoundTag tag);

    public void writeClient(CompoundTag tag) {}

    public void readClient(CompoundTag tag) {}

    @Nullable
    public ItemPort itemPort(Direction side) {
        return null;
    }

    @Nullable
    public FluidPort fluidPort(Direction side) {
        return null;
    }

    @Nullable
    public EnergyPort energyPort(Direction side) {
        return null;
    }
}
