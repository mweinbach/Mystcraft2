package ports.buildcraft.energy;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.Platform;

/** Upstream {@code TileEngineIron_BC8}: burns liquid fuel, needs coolant above ideal heat and leaves residue. */
public class CombustionEngineBlockEntity extends EngineBlockEntity implements MenuProvider {
    public static final int MAX_FLUID = 10_000;
    public static final double COOLDOWN_RATE = 0.05;
    public static final int MAX_COOLANT_PER_TICK = 40;

    final Tank tankFuel = new Tank("fuel", MAX_FLUID, fluid -> FuelRegistry.fuel(fluid) != null);
    final Tank tankCoolant = new Tank("coolant", MAX_FLUID, fluid -> FuelRegistry.coolant(fluid) > 0);
    final Tank tankResidue = new Tank("residue", MAX_FLUID, this::isResidue);
    private final FluidPort fluidPort = new EnginePort();
    private int penaltyCooling;
    private boolean lastPowered;
    private double burnTime;
    private double residueAmount;
    @Nullable private FuelRegistry.Fuel currentFuel;

    final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> Registry.FLUID.getId(tankFuel.fluid());
                case 1 -> tankFuel.amount();
                case 2 -> Registry.FLUID.getId(tankCoolant.fluid());
                case 3 -> tankCoolant.amount();
                case 4 -> Registry.FLUID.getId(tankResidue.fluid());
                case 5 -> tankResidue.amount();
                default -> (int) Math.round(heat * 100);
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 7; }
    };

    public CombustionEngineBlockEntity(BlockPos pos, BlockState state) {
        super(BCEnergy.COMBUSTION_ENGINE_TYPE.get(), pos, state);
    }

    public Tank fuelTank() { return tankFuel; }
    public Tank coolantTank() { return tankCoolant; }
    public Tank residueTank() { return tankResidue; }

    /** Upstream {@code TankManager.addDrops}: the fuel, coolant and residue as fragile fluid shards. */
    @Override
    public NonNullList<ItemStack> drops() {
        NonNullList<ItemStack> drops = NonNullList.create();
        for (Tank tank : List.of(tankFuel, tankCoolant, tankResidue)) FragileFluidShardItem.addFluidDrops(drops, tank.contents());
        return drops;
    }

    @Override protected boolean isRedstoneOnly() { return false; }
    @Override public boolean hasMenu() { return true; }
    @Override public long getMaxPower() { return 10_000 * Power.MJ; }
    @Override public long maxPowerExtracted() { return 500 * Power.MJ; }
    @Override protected int getMaxChainLength() { return 4; }
    @Override public boolean isActive() { return penaltyCooling <= 0; }

    @Override
    public InteractionResult onActivated(ServerPlayer player, InteractionHand hand, Direction side) {
        ItemStack held = player.getItemInHand(hand);
        FluidAmount solid = FuelRegistry.solidCoolant(held);
        if (!solid.isEmpty() && tankCoolant.fill(solid.fluid(), (int) solid.amount(), true) == solid.amount()) {
            tankCoolant.fill(solid.fluid(), (int) solid.amount(), false);
            if (!player.getAbilities().instabuild) held.shrink(1);
            setChanged();
            return InteractionResult.CONSUME;
        }
        if (!held.isEmpty() && Platform.get().interactWithFluidContainer(player, hand, level, worldPosition, side)) {
            return InteractionResult.CONSUME;
        }
        Platform.openMenu(player, this, worldPosition);
        return InteractionResult.CONSUME;
    }

    @Override
    public double getPistonSpeed() {
        return switch (getPowerStage()) {
            case BLUE -> 0.04;
            case GREEN -> 0.05;
            case YELLOW -> 0.06;
            case RED -> 0.07;
            default -> 0;
        };
    }

    @Override
    public boolean isBurning() {
        return !tankFuel.isEmpty() && penaltyCooling == 0 && isRedstonePowered;
    }

    @Override
    protected void burn() {
        // Upstream keeps burning the last millibucket's time after the tank empties.
        if (!tankFuel.isEmpty()) currentFuel = FuelRegistry.fuel(tankFuel.fluid());
        else if (burnTime <= 0) currentFuel = null;
        if (currentFuel == null) return;
        if (penaltyCooling <= 0) {
            if (isRedstonePowered) {
                lastPowered = true;
                if (burnTime > 0 || !tankFuel.isEmpty()) {
                    if (burnTime > 0) burnTime--;
                    if (burnTime <= 0) {
                        if (!tankFuel.isEmpty()) {
                            tankFuel.drain(null, 1, false);
                            burnTime += currentFuel.totalBurningTime() / 1000.0;
                            if (!currentFuel.residue().isEmpty()) {
                                FluidAmount residue = currentFuel.residue();
                                residueAmount += residue.amount() / 1000.0;
                                if (residueAmount >= 1) {
                                    residueAmount -= tankResidue.fill(residue.fluid(), Mth.floor(residueAmount), false);
                                }
                            }
                        } else {
                            currentFuel = null;
                            currentOutput = 0;
                            return;
                        }
                    }
                    currentOutput = currentFuel.powerPerCycle();
                    addPower(currentFuel.powerPerCycle());
                    heat += currentFuel.powerPerCycle() * HEAT_PER_MJ / Power.MJ;
                }
            } else if (lastPowered) {
                lastPowered = false;
                penaltyCooling = 10;
            }
        }
    }

    @Override
    public void updateHeatLevel() {
        double target;
        if (heat > MIN_HEAT && (penaltyCooling > 0 || !isRedstonePowered)) {
            heat -= COOLDOWN_RATE;
            target = MIN_HEAT;
        } else if (heat > IDEAL_HEAT) {
            target = IDEAL_HEAT;
        } else {
            target = heat;
        }
        if (target != heat) {
            double coolingBuffer = 0;
            double extraHeat = heat - target;
            if (extraHeat > 0 && !tankCoolant.isEmpty()) {
                float coolPerMb = FuelRegistry.coolant(tankCoolant.fluid());
                if (coolPerMb > 0) {
                    int coolantAmount = Math.min(MAX_COOLANT_PER_TICK, tankCoolant.amount());
                    coolingBuffer += coolantAmount * coolPerMb;
                    tankCoolant.drain(null, coolantAmount, false);
                }
            }
            heat -= coolingBuffer;
            getPowerStage();
        }
        if (heat <= MIN_HEAT && penaltyCooling > 0) penaltyCooling--;
        if (heat <= MIN_HEAT) heat = MIN_HEAT;
    }

    @Override
    public long getCurrentOutput() {
        return currentFuel == null ? 0 : currentFuel.powerPerCycle();
    }

    private boolean isResidue(Fluid fluid) {
        FuelRegistry.Fuel fuel = currentFuel != null ? currentFuel : FuelRegistry.fuel(tankFuel.fluid());
        return fuel != null && !fuel.residue().isEmpty() && fuel.residue().fluid() == fluid;
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        return fluidPort;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.combustion_engine");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CombustionEngineMenu(id, inventory, worldPosition, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("penaltyCooling", penaltyCooling);
        tag.putDouble("burnTime", burnTime);
        tag.putDouble("residueAmount", residueAmount);
        tag.put("tankFuel", tankFuel.save());
        tag.put("tankCoolant", tankCoolant.save());
        tag.put("tankResidue", tankResidue.save());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        super.loadServer(tag);
        penaltyCooling = tag.getInt("penaltyCooling");
        burnTime = tag.getDouble("burnTime");
        residueAmount = Math.max(0, tag.getDouble("residueAmount"));
        tankFuel.load(tag.getCompound("tankFuel"));
        tankCoolant.load(tag.getCompound("tankCoolant"));
        tankResidue.load(tag.getCompound("tankResidue"));
    }

    /** Fills go to the fuel tank, then the coolant tank; only residue can be drained. */
    private final class EnginePort implements FluidPort {
        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            int max = (int) Math.min(Integer.MAX_VALUE, amount);
            int filled = tankFuel.fill(fluid, max, simulate);
            if (filled == 0) filled = tankCoolant.fill(fluid, max, simulate);
            if (filled > 0 && !simulate) setChanged();
            return filled;
        }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            FluidAmount drained = tankResidue.drain(filter, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            if (!drained.isEmpty() && !simulate) setChanged();
            return drained;
        }

        @Override
        public List<FluidAmount> contents() {
            return List.of(tankFuel.contents(), tankCoolant.contents(), tankResidue.contents());
        }

        @Override
        public long capacity(int index) {
            return MAX_FLUID;
        }

        @Override
        public Object snapshot() {
            return new long[] {tankFuel.snapshotValue(), tankCoolant.snapshotValue(), tankResidue.snapshotValue()};
        }

        @Override
        public void restore(Object snapshot) {
            long[] values = (long[]) snapshot;
            tankFuel.restoreValue(values[0]);
            tankCoolant.restoreValue(values[1]);
            tankResidue.restoreValue(values[2]);
        }

        @Override
        public void onCommitted() {
            setChanged();
        }
    }
}
