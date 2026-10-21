package ports.buildcraft.core.statements;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.lib.HasWork;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.statement.RedstoneStatementContainer;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.statement.TriggerProvider;

/**
 * Upstream {@code CoreTriggerProvider}. Inventory triggers need an inventory with slots, which keeps
 * them off pipes and other blocks that only accept items, as upstream's item handler capability did.
 */
public enum CoreTriggerProvider implements TriggerProvider {
    INSTANCE;

    @Override
    public void addInternalTriggers(Collection<Trigger.Internal> triggers, StatementContainer container) {
        triggers.add(BCCoreStatements.TRIGGER_TRUE);
        if (container instanceof RedstoneStatementContainer) {
            triggers.add(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE);
            triggers.add(BCCoreStatements.TRIGGER_REDSTONE_INACTIVE);
        }
    }

    @Override
    public void addExternalTriggers(Collection<Trigger.External> triggers, Direction side, BlockEntity tile) {
        ItemPort items = BCCoreStatements.items(tile, side);
        if (items != null && !items.slots().isEmpty()) triggers.addAll(List.of(BCCoreStatements.TRIGGER_INVENTORY_ALL));
        FluidPort fluids = BCCoreStatements.fluids(tile, side);
        if (fluids != null && !fluids.contents().isEmpty()) triggers.addAll(List.of(BCCoreStatements.TRIGGER_FLUID_ALL));
        if (tile instanceof HasWork) {
            triggers.add(BCCoreStatements.TRIGGER_MACHINE_ACTIVE);
            triggers.add(BCCoreStatements.TRIGGER_MACHINE_INACTIVE);
        }
        if (TriggerEnginePowerStage.isTriggeringTile(tile)) {
            triggers.add(BCCoreStatements.TRIGGER_POWER_BLUE);
            triggers.add(BCCoreStatements.TRIGGER_POWER_GREEN);
            triggers.add(BCCoreStatements.TRIGGER_POWER_YELLOW);
            triggers.add(BCCoreStatements.TRIGGER_POWER_RED);
            triggers.add(BCCoreStatements.TRIGGER_POWER_OVERHEAT);
        }
    }
}
