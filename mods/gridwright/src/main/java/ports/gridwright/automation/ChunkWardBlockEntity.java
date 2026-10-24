package ports.gridwright.automation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.registry.ModAutomation;

/**
 * A chunk-loading ward: while its owner's grid pays {@link #GP_PER_CHUNK} for each of the 3x3 chunks
 * around it, they stay loaded as {@code /forceload} would keep them. It must first be put under
 * contract by giving it a villager carried in a golden lasso. Its owner may run at most
 * {@code gridwrightChunkWardsPerPlayer} of them at once, and a ward its grid has not paid for in
 * {@link #GRACE_TICKS} lets its chunks go (and stays unloaded until someone comes back).
 */
public class ChunkWardBlockEntity extends GridBlockEntity {

    public static final double GP_PER_CHUNK = 4;
    public static final double GP = GP_PER_CHUNK * 9;
    /** How long a ward keeps holding chunks its grid has stopped paying for; covers a server's first ticks. */
    public static final int GRACE_TICKS = 200;
    private static final String CONTRACTED = "Contracted";

    /** What the ward is doing, as shown when it is used. */
    public enum Status { LOADING, UNPOWERED, NO_OWNER, NO_CONTRACT, LIMIT }

    private boolean contracted;
    /** Whether it asked for power last tick, so the grid totals it reads include its own demand. */
    private boolean drawing;
    private int unpaid;
    private Status status = Status.NO_CONTRACT;

    public ChunkWardBlockEntity(BlockPos pos, BlockState state) {
        super(ModAutomation.CHUNK_WARD_ENTITY.get(), pos, state);
    }

    public boolean contracted() {
        return contracted;
    }

    /** Signs the contract; returns false if the ward already had one. */
    public boolean contract() {
        if (contracted) return false;
        contracted = true;
        setChanged();
        return true;
    }

    public Status status() {
        return status;
    }

    public boolean loading(ServerLevel level) {
        return ChunkWards.get(level.getServer()).isActive(level, worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof ChunkWardBlockEntity ward) ward.tick(server);
    }

    private void tick(ServerLevel level) {
        ChunkWards wards = ChunkWards.get(level.getServer());
        boolean loading = wards.isActive(level, worldPosition);
        String grid = gridKey(level.getServer());
        if (!contracted || grid == null) {
            halt(level, wards, contracted ? Status.NO_OWNER : Status.NO_CONTRACT);
            return;
        }
        // A ward already loading counts itself; one over a lowered limit lets go until the owner is back under it.
        int limit = ChunkWards.limit(level.getServer());
        if (wards.activeFor(owner()) > (loading ? limit : limit - 1)) {
            halt(level, wards, Status.LIMIT);
            return;
        }
        GridManager manager = GridManager.get(level.getServer());
        boolean powered = drawing && manager.snapshot(grid).powered();
        manager.addDemand(grid, GP);
        drawing = true;
        if (powered) {
            unpaid = 0;
            if (!loading) wards.activate(level, worldPosition, owner());
            status = Status.LOADING;
        } else {
            status = Status.UNPOWERED;
            if (loading && ++unpaid > GRACE_TICKS) wards.deactivate(level, worldPosition);
        }
    }

    private void halt(ServerLevel level, ChunkWards wards, Status reason) {
        drawing = false;
        unpaid = 0;
        status = reason;
        wards.deactivate(level, worldPosition);
    }

    /** Lets the chunks go; called when the block is removed (not when its chunk merely unloads). */
    public void release(ServerLevel level) {
        ChunkWards.get(level.getServer()).deactivate(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (contracted) tag.putBoolean(CONTRACTED, true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        contracted = tag.getBoolean(CONTRACTED);
    }
}
