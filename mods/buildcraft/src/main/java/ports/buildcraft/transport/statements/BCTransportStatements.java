package ports.buildcraft.transport.statements;

import net.minecraft.core.Direction;
import ports.buildcraft.statement.StatementManager;

/**
 * Upstream {@code BCTransportStatements}, for the ported pipes: pipe signals, paint, extraction presets
 * and power limits belong to wires and to pipes that are not ported yet.
 */
public final class BCTransportStatements {
    public static final TriggerPowerRequested TRIGGER_POWER_REQUESTED = new TriggerPowerRequested();
    public static final TriggerItemsTraversing TRIGGER_ITEMS_TRAVERSING = new TriggerItemsTraversing();
    public static final TriggerFluidsTraversing TRIGGER_FLUIDS_TRAVERSING = new TriggerFluidsTraversing();
    public static final ActionPipeDirection[] ACTION_PIPE_DIRECTION = new ActionPipeDirection[6];

    static {
        for (Direction face : Direction.values()) ACTION_PIPE_DIRECTION[face.ordinal()] = new ActionPipeDirection(face);
    }

    private BCTransportStatements() {}

    public static void init() {
        StatementManager.registerTriggerProvider(TriggerProviderPipes.INSTANCE);
        StatementManager.registerActionProvider(ActionProviderPipes.INSTANCE);
    }
}
