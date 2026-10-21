package ports.buildcraft.core.statements;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.energy.PowerStage;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemFluids;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/**
 * Upstream {@code BCCoreStatements}. Machine control and the stored-power triggers are left out: no
 * ported block is controllable, and upstream offered stored power for no BuildCraft block.
 */
public final class BCCoreStatements {
    public static final TriggerTrue TRIGGER_TRUE = new TriggerTrue();

    public static final TriggerMachine TRIGGER_MACHINE_ACTIVE = new TriggerMachine(true);
    public static final TriggerMachine TRIGGER_MACHINE_INACTIVE = new TriggerMachine(false);
    public static final TriggerMachine[] TRIGGER_MACHINE = {TRIGGER_MACHINE_ACTIVE, TRIGGER_MACHINE_INACTIVE};

    public static final TriggerRedstoneInput TRIGGER_REDSTONE_ACTIVE = new TriggerRedstoneInput(true);
    public static final TriggerRedstoneInput TRIGGER_REDSTONE_INACTIVE = new TriggerRedstoneInput(false);
    public static final TriggerRedstoneInput[] TRIGGER_REDSTONE = {TRIGGER_REDSTONE_ACTIVE, TRIGGER_REDSTONE_INACTIVE};

    public static final ActionRedstoneOutput ACTION_REDSTONE = new ActionRedstoneOutput();

    public static final TriggerInventory TRIGGER_INVENTORY_EMPTY = new TriggerInventory(TriggerInventory.State.EMPTY);
    public static final TriggerInventory TRIGGER_INVENTORY_CONTAINS = new TriggerInventory(TriggerInventory.State.CONTAINS);
    public static final TriggerInventory TRIGGER_INVENTORY_SPACE = new TriggerInventory(TriggerInventory.State.SPACE);
    public static final TriggerInventory TRIGGER_INVENTORY_FULL = new TriggerInventory(TriggerInventory.State.FULL);

    public static final TriggerFluidContainer TRIGGER_FLUID_EMPTY = new TriggerFluidContainer(TriggerFluidContainer.State.EMPTY);
    public static final TriggerFluidContainer TRIGGER_FLUID_CONTAINS = new TriggerFluidContainer(TriggerFluidContainer.State.CONTAINS);
    public static final TriggerFluidContainer TRIGGER_FLUID_SPACE = new TriggerFluidContainer(TriggerFluidContainer.State.SPACE);
    public static final TriggerFluidContainer TRIGGER_FLUID_FULL = new TriggerFluidContainer(TriggerFluidContainer.State.FULL);

    public static final TriggerInventoryLevel TRIGGER_INVENTORY_BELOW_25 = new TriggerInventoryLevel(TriggerInventoryLevel.TriggerType.BELOW25);
    public static final TriggerInventoryLevel TRIGGER_INVENTORY_BELOW_50 = new TriggerInventoryLevel(TriggerInventoryLevel.TriggerType.BELOW50);
    public static final TriggerInventoryLevel TRIGGER_INVENTORY_BELOW_75 = new TriggerInventoryLevel(TriggerInventoryLevel.TriggerType.BELOW75);

    public static final TriggerFluidContainerLevel TRIGGER_FLUID_BELOW_25 = new TriggerFluidContainerLevel(TriggerFluidContainerLevel.TriggerType.BELOW25);
    public static final TriggerFluidContainerLevel TRIGGER_FLUID_BELOW_50 = new TriggerFluidContainerLevel(TriggerFluidContainerLevel.TriggerType.BELOW50);
    public static final TriggerFluidContainerLevel TRIGGER_FLUID_BELOW_75 = new TriggerFluidContainerLevel(TriggerFluidContainerLevel.TriggerType.BELOW75);

    public static final TriggerEnginePowerStage TRIGGER_POWER_BLUE = new TriggerEnginePowerStage(PowerStage.BLUE);
    public static final TriggerEnginePowerStage TRIGGER_POWER_GREEN = new TriggerEnginePowerStage(PowerStage.GREEN);
    public static final TriggerEnginePowerStage TRIGGER_POWER_YELLOW = new TriggerEnginePowerStage(PowerStage.YELLOW);
    public static final TriggerEnginePowerStage TRIGGER_POWER_RED = new TriggerEnginePowerStage(PowerStage.RED);
    public static final TriggerEnginePowerStage TRIGGER_POWER_OVERHEAT = new TriggerEnginePowerStage(PowerStage.OVERHEAT);
    public static final TriggerEnginePowerStage[] TRIGGER_POWER_STAGES = {};

    public static final Trigger.External[] TRIGGER_INVENTORY_ALL = {
        TRIGGER_INVENTORY_EMPTY, TRIGGER_INVENTORY_SPACE, TRIGGER_INVENTORY_CONTAINS, TRIGGER_INVENTORY_FULL,
        TRIGGER_INVENTORY_BELOW_25, TRIGGER_INVENTORY_BELOW_50, TRIGGER_INVENTORY_BELOW_75
    };
    public static final Trigger.External[] TRIGGER_FLUID_ALL = {
        TRIGGER_FLUID_EMPTY, TRIGGER_FLUID_SPACE, TRIGGER_FLUID_CONTAINS, TRIGGER_FLUID_FULL,
        TRIGGER_FLUID_BELOW_25, TRIGGER_FLUID_BELOW_50, TRIGGER_FLUID_BELOW_75
    };

    private BCCoreStatements() {}

    public static void init() {
        StatementManager.registerParameter(StatementParamGateSideOnly::readFromNbt);
        StatementManager.registerTriggerProvider(CoreTriggerProvider.INSTANCE);
        StatementManager.registerActionProvider(CoreActionProvider.INSTANCE);
    }

    /** @param side the face of the gate's block that {@code tile} is on */
    @Nullable
    static ItemPort items(BlockEntity tile, Direction side) {
        return tile.getLevel() == null ? null : Ports.items(tile.getLevel(), tile.getBlockPos(), side.getOpposite());
    }

    @Nullable
    static FluidPort fluids(BlockEntity tile, Direction side) {
        return tile.getLevel() == null ? null : Ports.fluids(tile.getLevel(), tile.getBlockPos(), side.getOpposite());
    }

    /** Upstream {@code FluidUtil.getFluidContained} of the item parameter: null for no item or an empty container. */
    @Nullable
    static Fluid searchedFluid(StatementParameter[] parameters) {
        if (parameters.length < 1 || parameters[0] == null) return null;
        ItemStack stack = parameters[0].getItemStack();
        if (stack.isEmpty()) return null;
        ItemFluids fluids = Platform.get().itemFluids(stack);
        if (fluids == null) return null;
        FluidAmount contained = fluids.drain(Integer.MAX_VALUE, true);
        return contained.isEmpty() ? null : contained.fluid();
    }
}
