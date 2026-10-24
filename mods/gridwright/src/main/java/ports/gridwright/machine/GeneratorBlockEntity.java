package ports.gridwright.machine;

import ports.gridwright.block.MachineBlock;
import ports.gridwright.platform.Platform;
import ports.gridwright.power.Overclock;
import ports.gridwright.storage.FluidTank;
import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A fuel generator. It burns one item at a time from its fuel slot, stores what it makes and pushes
 * it into every neighbour that accepts Forge Energy or Team Reborn Energy. It pauses while its store
 * is full, so nothing is wasted, and only starts an item when the item's container (bucket, bottle,
 * bowl) fits in the output slot.
 */
public class GeneratorBlockEntity extends MachineBlockEntity implements EnergyHolder {

    public static final int DATA_COUNT = 6;
    private static final int FUEL = 0;
    private static final int REMAINDER = 1;
    private static final int[] SLOTS = {FUEL, REMAINDER};
    public static final int LAVA_BUCKETS = 4;
    /** How long after storing energy a generator still counts as running. */
    public static final int RUNNING_GRACE = 20;

    private final EnergyBuffer energy = new EnergyBuffer(GeneratorKind.CAPACITY, GeneratorKind.MAX_OUTPUT);
    private int burnLeft;
    private int burnTotal;
    private int rate;
    /** Installed overclock upgrades (phase 2d), 0 to {@link Overclock#MAX}. */
    private int overclock;
    /** Lava piped into a lava generator (phase 2d); other generators refuse fluid. */
    private final FluidTank lava = new FluidTank(LAVA_BUCKETS, this::setChanged);
    /** Game time this generator last stored energy, for the rainbow generator. Not saved. */
    private long lastGenerated = Long.MIN_VALUE / 2;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) (energy.stored() & 0xFFFF);
                case 1 -> (int) ((energy.stored() >>> 16) & 0xFFFF);
                case 2 -> (int) (energy.capacity() & 0xFFFF);
                case 3 -> (int) ((energy.capacity() >>> 16) & 0xFFFF);
                case 4 -> burnTotal <= 0 ? 0 : (int) (1000L * burnLeft / burnTotal);
                case 5 -> burnLeft > 0 ? rate : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; the client copy is a SimpleContainerData.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state, 2);
        energy.onChange(this::setChanged);
    }

    public GeneratorKind kind() {
        return ((MachineBlock) getBlockState().getBlock()).generator();
    }

    @Override
    public EnergyBuffer energy() {
        return energy;
    }

    public int burnLeft() {
        return burnLeft;
    }

    /** Energy per tick of the current burn, or 0 when idle. */
    public int rate() {
        return burnLeft > 0 ? rate : 0;
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.GENERATOR;
    }

    @Override
    public ContainerData data() {
        return data;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof GeneratorBlockEntity generator) generator.tick(server, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (burnLeft <= 0) startBurn();
        if (burnLeft > 0 && energy.space() >= rate) {
            energy.fill(rate);
            burnLeft--;
            lastGenerated = level.getGameTime();
            setChanged();
        }
        push(level, pos);
        boolean lit = burnLeft > 0;
        if (state.hasProperty(MachineBlock.LIT) && state.getValue(MachineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(MachineBlock.LIT, lit), 3);
        }
    }

    private void startBurn() {
        GeneratorKind.Fuel fuel = kind().fuel(items.get(FUEL));
        boolean fromTank = false;
        if (fuel == null && kind() == GeneratorKind.LAVA && lava.amount() >= Platform.bucketVolume()) {
            GeneratorKind.Fuel bucket = kind().fuel(new ItemStack(Items.LAVA_BUCKET));
            if (bucket != null) {
                fuel = new GeneratorKind.Fuel(bucket.perTick(), bucket.ticks(), ItemStack.EMPTY);
                fromTank = true;
            }
        }
        if (fuel == null) return;
        fuel = Overclock.apply(fuel, overclock);
        if (energy.space() < fuel.perTick() || !fits(fuel.remainder())) return;
        if (fromTank) {
            lava.extract(Platform.bucketVolume(), false);
            lava.changed();
        } else {
            items.get(FUEL).shrink(1);
        }
        if (!fuel.remainder().isEmpty()) {
            ItemStack held = items.get(REMAINDER);
            if (held.isEmpty()) items.set(REMAINDER, fuel.remainder().copy());
            else held.grow(fuel.remainder().getCount());
        }
        burnLeft = burnTotal = fuel.ticks();
        rate = fuel.perTick();
        setChanged();
    }

    private boolean fits(ItemStack remainder) {
        if (remainder.isEmpty()) return true;
        ItemStack held = items.get(REMAINDER);
        return held.isEmpty() || ItemStack.isSameItemSameTags(held, remainder)
                && held.getCount() + remainder.getCount() <= Math.min(held.getMaxStackSize(), getMaxStackSize());
    }

    private void push(ServerLevel level, BlockPos pos) {
        long budget = Math.min(energy.stored(), GeneratorKind.MAX_OUTPUT);
        for (Direction direction : Direction.values()) {
            if (budget <= 0) break;
            long accepted = Platform.pushEnergy(level, pos.relative(direction), direction.getOpposite(), budget);
            accepted = Math.max(0, Math.min(accepted, budget));
            if (accepted > 0) {
                energy.restore(energy.stored() - accepted);
                energy.markChanged();
                budget -= accepted;
            }
        }
    }

    /** True while this generator stored energy within the last {@link #RUNNING_GRACE} ticks. */
    public boolean running(long gameTime) {
        return gameTime - lastGenerated <= RUNNING_GRACE;
    }

    public int overclock() {
        return overclock;
    }

    /** Installs up to {@code offered} upgrades; returns how many were taken. */
    public int installOverclock(int offered) {
        int taken = Math.max(0, Math.min(offered, Overclock.MAX - overclock));
        if (taken > 0) {
            overclock += taken;
            setChanged();
        }
        return taken;
    }

    /** Removes every installed upgrade; returns how many there were. */
    public int removeOverclock() {
        int removed = overclock;
        if (removed > 0) {
            overclock = 0;
            setChanged();
        }
        return removed;
    }

    /** The lava tank. Only a lava generator accepts fluid; see {@link #insertLava}. */
    public FluidTank lavaTank() {
        return lava;
    }

    /** True if piped fluid of this kind may enter: lava, into a lava generator, only. */
    public boolean acceptsFluid(Fluid fluid) {
        return fluid == Fluids.LAVA && kind() == GeneratorKind.LAVA;
    }

    /** Fills the lava tank; never notifies (callers call {@code lavaTank().changed()} once final). */
    public long insertLava(Fluid fluid, @Nullable CompoundTag tag, long max, boolean simulate) {
        if (!acceptsFluid(fluid) || tag != null && !tag.isEmpty()) return 0;
        return lava.insert(fluid, null, max, simulate);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL && kind().fuel(stack) != null;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == REMAINDER;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", energy.stored());
        tag.putInt("BurnLeft", burnLeft);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Rate", rate);
        if (overclock > 0) tag.putInt("Overclock", overclock);
        if (!lava.isEmpty()) {
            CompoundTag tank = new CompoundTag();
            lava.save(tank);
            tag.put("Lava", tank);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.restore(tag.getLong("Energy"));
        burnLeft = tag.getInt("BurnLeft");
        burnTotal = tag.getInt("BurnTotal");
        rate = tag.getInt("Rate");
        overclock = Math.max(0, Math.min(Overclock.MAX, tag.getInt("Overclock")));
        lava.load(tag.getCompound("Lava"));
    }
}
