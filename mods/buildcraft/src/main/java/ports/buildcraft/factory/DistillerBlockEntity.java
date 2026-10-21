package ports.buildcraft.factory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.energy.RefineryRecipes;
import ports.buildcraft.lib.ClientTank;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.HasWork;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileDistiller_BC8}: splits the fluid in its input tank into a gas, pushed out of the
 * top, and a liquid, pushed out of the bottom, using up to 6 MJ/t. Pipes fill it from the sides.
 */
public class DistillerBlockEntity extends SyncedBlockEntity implements PortHolder, MenuProvider, HasWork {
    public static final int CAPACITY = 4000;
    public static final long MAX_MJ_PER_TICK = 6 * Power.MJ;
    /** Upstream {@code BCCoreConfig.networkUpdateRate}. */
    private static final int SYNC_INTERVAL = 10;
    private static final int POWER_AVERAGE_TICKS = 100;

    final Tank tankIn = new Tank("in", CAPACITY, fluid -> RefineryRecipes.distillation(fluid) != null);
    final Tank tankGasOut = new Tank("gasOut", CAPACITY);
    final Tank tankLiquidOut = new Tank("liquidOut", CAPACITY);
    private final MjBattery battery = new MjBattery(1024 * Power.MJ);
    private final EnergyPort energyPort = battery.receiver(false, this::setChanged);
    private final FluidPort inputPort = new DistillerPort(true);
    private final FluidPort gasPort = new DistillerPort(false, tankGasOut);
    private final FluidPort liquidPort = new DistillerPort(false, tankLiquidOut);
    private final FluidPort allPort = new DistillerPort(true, tankGasOut, tankLiquidOut);

    private long distillPower;
    private boolean active;
    private boolean hasWork;
    private final long[] powerHistory = new long[POWER_AVERAGE_TICKS];
    private int powerHistoryPos;
    private long powerHistoryTotal;
    private long syncedState;
    private int ticksSinceSync;

    final ClientTank clientIn = new ClientTank(CAPACITY);
    final ClientTank clientGasOut = new ClientTank(CAPACITY);
    final ClientTank clientLiquidOut = new ClientTank(CAPACITY);
    private boolean clientActive;
    private long clientPowerAverage;
    private float powerPartState;
    private float lastPowerPartState;

    final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> Registry.FLUID.getId(tankIn.fluid());
                case 1 -> tankIn.amount();
                case 2 -> Registry.FLUID.getId(tankGasOut.fluid());
                case 3 -> tankGasOut.amount();
                case 4 -> Registry.FLUID.getId(tankLiquidOut.fluid());
                case 5 -> tankLiquidOut.amount();
                default -> active ? 1 : 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 7; }
    };

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(BCFactory.DISTILLER_TYPE.get(), pos, state);
    }

    public Tank inputTank() { return tankIn; }
    public Tank gasTank() { return tankGasOut; }
    public Tank liquidTank() { return tankLiquidOut; }
    public MjBattery battery() { return battery; }
    public boolean isActive() { return active; }
    @Override public boolean hasWork() { return hasWork; }

    void serverTick() {
        RefineryRecipes.Distillation recipe = RefineryRecipes.distillation(tankIn.fluid());
        long attempted = 0;
        hasWork = false;
        if (recipe == null) {
            refundProgress();
        } else {
            FluidAmount in = recipe.in(), gas = recipe.outGas(), liquid = recipe.outLiquid();
            boolean canExtract = tankIn.drain(in.fluid(), (int) in.amount(), true).amount() == in.amount();
            boolean canFillLiquid = tankLiquidOut.fill(liquid.fluid(), (int) liquid.amount(), true) == liquid.amount();
            boolean canFillGas = tankGasOut.fill(gas.fluid(), (int) gas.amount(), true) == gas.amount();
            if (canExtract && canFillLiquid && canFillGas) {
                hasWork = true;
                // Upstream draws more power the fuller its battery is, up to 6 MJ/t at half capacity.
                long max = MAX_MJ_PER_TICK * (battery.stored() + MAX_MJ_PER_TICK) / (battery.capacity() / 2);
                max = Math.min(max, MAX_MJ_PER_TICK);
                long power = battery.extractPower(0, max);
                attempted = max;
                distillPower += power;
                active = power > 0;
                if (distillPower >= recipe.powerRequired()) {
                    active = true;
                    distillPower -= recipe.powerRequired();
                    tankIn.drain(in.fluid(), (int) in.amount(), false);
                    tankGasOut.fill(gas.fluid(), (int) gas.amount(), false);
                    tankLiquidOut.fill(liquid.fluid(), (int) liquid.amount(), false);
                }
                setChanged();
            } else {
                refundProgress();
            }
        }
        pushPowerAverage(attempted);

        ticksSinceSync++;
        long state = syncState();
        if (state != syncedState && ticksSinceSync >= SYNC_INTERVAL) {
            syncedState = state;
            ticksSinceSync = 0;
            queueSync();
        }
        flushSync();
    }

    void clientTick() {
        clientIn.tick();
        clientGasOut.tick();
        clientLiquidOut.tick();
        // Upstream models/tiles/distiller.json: "state" climbs while active and resets from 1.5 to 0.5.
        // Its power_average and power_max are whole MJ.
        lastPowerPartState = powerPartState;
        double speed = clientActive ? clientPowerAverage / Power.MJ * 0.06 / (MAX_MJ_PER_TICK / Power.MJ) : 0.01;
        if (clientActive) powerPartState += (float) speed;
        else powerPartState = powerPartState > speed ? powerPartState - (float) speed : 0;
        if (lastPowerPartState >= 1.5F && powerPartState >= 1.5F) {
            lastPowerPartState -= 1;
            powerPartState -= 1;
        }
    }

    /**
     * Progress made on a recipe that can no longer run returns to the battery, even a full one: upstream
     * discarded it then. The refund is at most one recipe's power, below where the battery bleeds.
     */
    private void refundProgress() {
        battery.addPower(distillPower);
        if (distillPower != 0) setChanged();
        distillPower = 0;
        active = false;
    }

    private void pushPowerAverage(long value) {
        powerHistoryPos = (powerHistoryPos + 1) % POWER_AVERAGE_TICKS;
        powerHistoryTotal += value - powerHistory[powerHistoryPos];
        powerHistory[powerHistoryPos] = value;
    }

    /** Upstream sends the average rounded to half an MJ. */
    long powerAverageForClient() {
        long half = Power.MJ / 2;
        return Math.round(powerHistoryTotal / (double) POWER_AVERAGE_TICKS / half) * half;
    }

    private long syncState() {
        long hash = tankIn.snapshotValue();
        hash = hash * 31 + tankGasOut.snapshotValue();
        hash = hash * 31 + tankLiquidOut.snapshotValue();
        hash = hash * 31 + powerAverageForClient();
        return hash * 2 + (active ? 1 : 0);
    }

    public boolean isActiveClient() { return clientActive; }
    public long powerAverageClient() { return clientPowerAverage; }
    public ClientTank inputForRender() { return clientIn; }
    public ClientTank gasForRender() { return clientGasOut; }
    public ClientTank liquidForRender() { return clientLiquidOut; }

    public float powerPartState(float partialTicks) {
        return lastPowerPartState + (powerPartState - lastPowerPartState) * partialTicks;
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        if (side == null) return allPort;
        return switch (side) {
            case UP -> gasPort;
            case DOWN -> liquidPort;
            default -> inputPort;
        };
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energyPort;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.distiller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DistillerMenu(id, inventory, worldPosition, data, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag tanks = new CompoundTag();
        tanks.put("in", tankIn.save());
        tanks.put("gasOut", tankGasOut.save());
        tanks.put("liquidOut", tankLiquidOut.save());
        tag.put("tanks", tanks);
        tag.put("battery", battery.save());
        tag.putLong("distillPower", distillPower);
        tag.putLongArray("powerAvg", powerHistory);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        CompoundTag tanks = tag.getCompound("tanks");
        tankIn.load(tanks.getCompound("in"));
        tankGasOut.load(tanks.getCompound("gasOut"));
        tankLiquidOut.load(tanks.getCompound("liquidOut"));
        battery.load(tag.getCompound("battery"));
        distillPower = tag.getLong("distillPower");
        long[] history = tag.getLongArray("powerAvg");
        powerHistoryTotal = 0;
        for (int i = 0; i < POWER_AVERAGE_TICKS; i++) {
            powerHistory[i] = i < history.length ? history[i] : 0;
            powerHistoryTotal += powerHistory[i];
        }
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        ClientTank.write(tag, "in", tankIn);
        ClientTank.write(tag, "gasOut", tankGasOut);
        ClientTank.write(tag, "liquidOut", tankLiquidOut);
        tag.putBoolean("active", active);
        tag.putLong("powerAvg", powerAverageForClient());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        clientIn.read(tag, "in");
        clientGasOut.read(tag, "gasOut");
        clientLiquidOut.read(tag, "liquidOut");
        clientActive = tag.getBoolean("active");
        clientPowerAverage = tag.getLong("powerAvg");
    }

    /**
     * Upstream {@code TankManager} faces: the input tank only fills and the output tanks only drain. The
     * unsided face, used by held containers, fills the input and drains the gas, then the liquid.
     */
    private final class DistillerPort implements FluidPort {
        private final boolean fills;
        private final List<Tank> drains;
        private final List<Tank> visible;

        DistillerPort(boolean fills, Tank... drains) {
            this.fills = fills;
            this.drains = List.of(drains);
            List<Tank> shown = new ArrayList<>();
            if (fills) shown.add(tankIn);
            shown.addAll(this.drains);
            this.visible = List.copyOf(shown);
        }

        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            if (!fills) return 0;
            int filled = tankIn.fill(fluid, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            if (filled > 0 && !simulate) setChanged();
            return filled;
        }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            int max = (int) Math.min(Integer.MAX_VALUE, amount);
            for (Tank tank : drains) {
                FluidAmount drained = tank.drain(filter, max, simulate);
                if (drained.isEmpty()) continue;
                if (!simulate) setChanged();
                return drained;
            }
            return FluidAmount.EMPTY;
        }

        @Override
        public List<FluidAmount> contents() {
            return visible.stream().map(Tank::contents).toList();
        }

        @Override
        public long capacity(int index) {
            return CAPACITY;
        }

        @Override
        public Object snapshot() {
            return new long[] {tankIn.snapshotValue(), tankGasOut.snapshotValue(), tankLiquidOut.snapshotValue()};
        }

        @Override
        public void restore(Object snapshot) {
            long[] values = (long[]) snapshot;
            tankIn.restoreValue(values[0]);
            tankGasOut.restoreValue(values[1]);
            tankLiquidOut.restoreValue(values[2]);
        }

        @Override
        public void onCommitted() {
            setChanged();
        }
    }
}
