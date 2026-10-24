package ports.gridwright.storage;

import ports.gridwright.block.WardBlock;
import ports.gridwright.block.WardKind;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.registry.ModUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A ward's presence and, for wards that cost Grid Power, whether its owner's grid is paying for it.
 * Wards tell {@link Wards} where they are while loaded, so a spawn or teleport check never has to
 * search the blocks around it.
 */
public class WardBlockEntity extends GridBlockEntity {

    /** Why a ward is or is not working, as shown when it is used. */
    public enum Status { ACTIVE, UNPOWERED, NO_OWNER }

    /** Whether it asked for power last tick, so the grid totals it reads include its own demand. */
    private boolean active;
    private boolean powered;
    private Status status = Status.UNPOWERED;

    public WardBlockEntity(BlockPos pos, BlockState state) {
        super(ModUtilities.WARD.get(), pos, state);
    }

    /** Read from the block state rather than stored, so the kind is right however the entity was made. */
    public WardKind kind() {
        return getBlockState().getBlock() instanceof WardBlock ward ? ward.kind() : WardKind.SPAWN;
    }

    /** Whether the ward is enforcing its effect right now. */
    public boolean working() {
        return !kind().powered() || powered;
    }

    public Status status() {
        return !kind().powered() ? Status.ACTIVE : status;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof WardBlockEntity ward) ward.tick(server);
    }

    private void tick(ServerLevel level) {
        WardKind kind = kind();
        if (!kind.powered()) return;
        String grid = gridKey(level.getServer());
        if (grid == null) {
            active = false;
            powered = false;
            status = Status.NO_OWNER;
            return;
        }
        GridManager manager = GridManager.get(level.getServer());
        powered = active && manager.snapshot(grid).powered();
        manager.addDemand(grid, kind.gp());
        active = true;
        status = powered ? Status.ACTIVE : Status.UNPOWERED;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null) Wards.add(level, worldPosition, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) Wards.remove(level, worldPosition, this);
    }
}
