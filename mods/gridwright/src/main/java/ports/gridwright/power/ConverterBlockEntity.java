package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.platform.Platform;
import ports.gridwright.registry.ModPower;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The power converter, Gridwright's only bridge between Grid Power and Forge Energy / Team Reborn
 * Energy, and a deliberately lossy one.
 *
 * <ul>
 *   <li>{@link ConverterMode#TO_ENERGY}: while it has room, it asks its owner's grid for
 *       {@link #GP_DRAW} GP as a consumer and, on every tick the grid is powered, makes
 *       {@link #ENERGY_PER_GP_OUT} energy per GP (64 per tick), which it pushes into its neighbours
 *       and lets other mods extract. It never accepts energy.</li>
 *   <li>{@link ConverterMode#TO_GRID}: it accepts energy and, while its grid has any demand, burns
 *       up to {@link #MAX_BURN} of it per tick into Grid Power at {@link #ENERGY_PER_GP_IN} energy per
 *       GP (at most 16 GP), as a producer with the usual diminishing returns. It never gives energy.</li>
 * </ul>
 *
 * <p>One GP therefore becomes 4 energy per tick, and 4 energy per tick becomes a quarter of a GP: any
 * loop through converters, generators or other mods' machines that conserve energy loses at least
 * three quarters of its power on every pass and runs down.
 */
public class ConverterBlockEntity extends GridBlockEntity {

    public static final long CAPACITY = 16_000;
    /** The most energy that moves in or out through one call or one push per tick. */
    public static final long MAX_TRANSFER = 1_024;
    /** GP a converter making energy asks of its grid. */
    public static final double GP_DRAW = 16.0;
    /** Energy made per tick for each GP drawn. */
    public static final long ENERGY_PER_GP_OUT = 4;
    /** Energy burned per tick for each GP made. */
    public static final long ENERGY_PER_GP_IN = 16;
    /** Energy made per powered tick in {@link ConverterMode#TO_ENERGY}. */
    public static final long ENERGY_MADE = (long) GP_DRAW * ENERGY_PER_GP_OUT;
    /** The most energy burned per tick in {@link ConverterMode#TO_GRID} (16 GP worth). */
    public static final long MAX_BURN = 256;

    private long stored;
    /** Whether it asked its grid for power last tick, so the totals it reads include its own demand. */
    private boolean active;
    private double lastGp;
    private long lastEnergy;

    public ConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModPower.POWER_CONVERTER_ENTITY.get(), pos, state);
    }

    public ConverterMode mode() {
        BlockState state = getBlockState();
        return state.hasProperty(ConverterBlock.MODE) ? state.getValue(ConverterBlock.MODE) : ConverterMode.TO_ENERGY;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return CAPACITY;
    }

    /** GP drawn (making energy) or made (making GP) in the last tick. */
    public double lastGp() {
        return lastGp;
    }

    /** Energy made (making energy) or burned (making GP) in the last tick. */
    public long lastEnergy() {
        return lastEnergy;
    }

    public boolean canReceive() {
        return mode() == ConverterMode.TO_GRID;
    }

    public boolean canExtract() {
        return mode() == ConverterMode.TO_ENERGY;
    }

    /** Energy other mods insert; only while making GP. Returns how much was (or would be) accepted. */
    public long receive(long max, boolean simulate) {
        if (!canReceive() || max <= 0) return 0;
        long accepted = Math.min(Math.min(max, MAX_TRANSFER), CAPACITY - stored);
        if (!simulate && accepted > 0) {
            stored += accepted;
            setChanged();
        }
        return Math.max(0, accepted);
    }

    /** Energy other mods extract; only while making energy. Returns how much was (or would be) taken. */
    public long extract(long max, boolean simulate) {
        if (!canExtract() || max <= 0) return 0;
        long taken = Math.min(Math.min(max, MAX_TRANSFER), stored);
        if (!simulate && taken > 0) {
            stored -= taken;
            setChanged();
        }
        return Math.max(0, taken);
    }

    /** Restores a rolled-back amount (Fabric transactions use this); never notifies. */
    public void restore(long amount) {
        stored = Math.max(0, Math.min(CAPACITY, amount));
    }

    public void markChanged() {
        setChanged();
    }

    /** Called when the mode changes, so a converter switched to making GP stops asking for power. */
    void modeChanged() {
        active = false;
        lastGp = 0;
        lastEnergy = 0;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof ConverterBlockEntity converter) converter.tick(server, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        lastGp = 0;
        lastEnergy = 0;
        String grid = gridKey(level.getServer());
        GridManager manager = GridManager.get(level.getServer());
        if (mode() == ConverterMode.TO_ENERGY) {
            if (grid != null && CAPACITY - stored >= ENERGY_MADE) {
                boolean powered = active && manager.snapshot(grid).powered();
                manager.addDemand(grid, GP_DRAW);
                active = true;
                if (powered) {
                    stored += ENERGY_MADE;
                    lastGp = GP_DRAW;
                    lastEnergy = ENERGY_MADE;
                    setChanged();
                }
            } else {
                active = false;
            }
            push(level, pos);
        } else {
            active = false;
            // Burning energy into a grid nothing draws from would only waste it.
            if (grid != null && stored > 0 && manager.snapshot(grid).demand() > 0) {
                long burned = Math.min(stored, MAX_BURN);
                stored -= burned;
                lastEnergy = burned;
                lastGp = (double) burned / ENERGY_PER_GP_IN;
                manager.addProduction(grid, Registry.BLOCK.getKey(state.getBlock()).toString(), lastGp);
                setChanged();
            }
        }
    }

    private void push(ServerLevel level, BlockPos pos) {
        long budget = Math.min(stored, MAX_TRANSFER);
        for (Direction direction : Direction.values()) {
            if (budget <= 0) break;
            long accepted = Platform.pushEnergy(level, pos.relative(direction), direction.getOpposite(), budget);
            accepted = Math.max(0, Math.min(accepted, budget));
            if (accepted > 0) {
                stored -= accepted;
                budget -= accepted;
                setChanged();
            }
        }
    }

    /** "Making energy: 16.0 GP -> 64 FE/t, 1,200 / 16,000 FE", for the action bar. */
    public Component describe() {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.ROOT);
        return Component.translatable("message.gridwright.converter." + mode().getSerializedName(),
                GridBlockEntity.format(lastGp), format.format(lastEnergy), format.format(stored), format.format(CAPACITY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = Math.max(0, Math.min(CAPACITY, tag.getLong("Energy")));
    }
}
