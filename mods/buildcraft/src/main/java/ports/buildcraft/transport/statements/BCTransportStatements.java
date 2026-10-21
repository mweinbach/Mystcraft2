package ports.buildcraft.transport.statements;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.StatementManager;

/**
 * Upstream {@code BCTransportStatements}, for the ported pipes and wires: paint, extraction presets and
 * power limits belong to pipes that are not ported yet.
 */
public final class BCTransportStatements {
    public static final TriggerPowerRequested TRIGGER_POWER_REQUESTED = new TriggerPowerRequested();
    public static final TriggerItemsTraversing TRIGGER_ITEMS_TRAVERSING = new TriggerItemsTraversing();
    public static final TriggerFluidsTraversing TRIGGER_FLUIDS_TRAVERSING = new TriggerFluidsTraversing();
    public static final ActionPipeDirection[] ACTION_PIPE_DIRECTION = new ActionPipeDirection[6];
    /** On then off for each colour, in colour order. */
    public static final TriggerPipeSignal[] TRIGGER_PIPE_SIGNAL = new TriggerPipeSignal[2 * 16];
    public static final ActionPipeSignal[] ACTION_PIPE_SIGNAL = new ActionPipeSignal[16];

    static {
        for (Direction face : Direction.values()) ACTION_PIPE_DIRECTION[face.ordinal()] = new ActionPipeDirection(face);
        for (DyeColor colour : DyeColor.values()) {
            TRIGGER_PIPE_SIGNAL[colour.getId() * 2] = new TriggerPipeSignal(true, colour);
            TRIGGER_PIPE_SIGNAL[colour.getId() * 2 + 1] = new TriggerPipeSignal(false, colour);
            ACTION_PIPE_SIGNAL[colour.getId()] = new ActionPipeSignal(colour);
        }
    }

    private BCTransportStatements() {}

    public static void init() {
        StatementManager.registerTriggerProvider(TriggerProviderPipes.INSTANCE);
        StatementManager.registerActionProvider(ActionProviderPipes.INSTANCE);
        StatementManager.registerParameter(TriggerParameterSignal::readFromNbt);
        StatementManager.registerParameter(ActionParameterSignal::readFromNbt);
    }
}
