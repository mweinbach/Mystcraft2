package ports.gridwright.storage;

import ports.gridwright.block.DrumBlock;
import ports.gridwright.block.DrumTier;
import ports.gridwright.registry.ModUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A drum's tank. Forge exposes it as an {@code IFluidHandler}, Fabric as a {@code Storage<FluidVariant>};
 * both read and write {@link #tank()}.
 */
public class DrumBlockEntity extends BlockEntity {

    /** The block entity tag key the drum's loot table copies into the dropped item. */
    public static final String TANK = "Tank";

    private final FluidTank tank;

    public DrumBlockEntity(BlockPos pos, BlockState state) {
        super(ModUtilities.DRUM.get(), pos, state);
        DrumTier tier = state.getBlock() instanceof DrumBlock drum ? drum.tier() : DrumTier.STONE;
        this.tank = new FluidTank(tier.buckets(), this::onTankChanged);
    }

    public FluidTank tank() {
        return tank;
    }

    private void onTankChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** Comparator output: 0 when empty, otherwise 1 to 15 by how full the drum is. */
    public int signal() {
        if (tank.isEmpty()) return 0;
        return 1 + (int) (tank.amount() * 14 / tank.capacity());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag contents = new CompoundTag();
        tank.save(contents);
        if (!contents.isEmpty()) tag.put(TANK, contents);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.load(tag.getCompound(TANK));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
