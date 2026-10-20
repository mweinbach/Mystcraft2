package ports.buildcraft.transport.flow;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code PipeFlowStructure}: carries nothing and connects only to other structure pipes. */
public class StructureFlow extends PipeFlow {
    public StructureFlow(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof StructureFlow;
    }

    @Override
    public boolean canConnectTile(Direction face, BlockEntity tile) {
        return false;
    }

    @Override
    public void save(CompoundTag tag) {}

    @Override
    public void load(CompoundTag tag) {}
}
