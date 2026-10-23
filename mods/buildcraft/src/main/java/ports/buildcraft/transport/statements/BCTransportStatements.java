package ports.buildcraft.transport.statements;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour;
import ports.buildcraft.transport.behaviour.LimiterBehaviour;

/**
 * Upstream {@code BCTransportStatements}, for the ported pipes and wires. The RF power limits belong to RF pipes,
 * which the port does not have.
 */
public final class BCTransportStatements {
    public static final TriggerPowerRequested TRIGGER_POWER_REQUESTED = new TriggerPowerRequested();
    public static final TriggerItemsTraversing TRIGGER_ITEMS_TRAVERSING = new TriggerItemsTraversing();
    public static final TriggerFluidsTraversing TRIGGER_FLUIDS_TRAVERSING = new TriggerFluidsTraversing();
    public static final ActionPipeDirection[] ACTION_PIPE_DIRECTION = new ActionPipeDirection[6];
    /** By colour id: the lapis and daizuli pipes' paint actions. */
    public static final ActionPipeColor[] ACTION_PIPE_COLOUR = new ActionPipeColor[16];
    /** By emzuli filter slot: red, green, blue and yellow. */
    public static final ActionExtractionPreset[] ACTION_EXTRACTION_PRESET = new ActionExtractionPreset[EmzuliBehaviour.SlotIndex.VALUES.length];
    /** On then off for each colour, in colour order. */
    public static final TriggerPipeSignal[] TRIGGER_PIPE_SIGNAL = new TriggerPipeSignal[2 * 16];
    public static final ActionPipeSignal[] ACTION_PIPE_SIGNAL = new ActionPipeSignal[16];
    /** From no transfer ({@code MAX_SHIFT}) to the full rate (shift 0), as upstream. */
    public static final ActionPowerLimit[] ACTION_IRON_POWER_LIMIT = new ActionPowerLimit[LimiterBehaviour.MAX_SHIFT + 1];
    public static final ActionPowerLimit[] ACTION_DIAMOND_POWER_LIMIT = new ActionPowerLimit[LimiterBehaviour.MAX_SHIFT + 1];

    static {
        for (Direction face : Direction.values()) ACTION_PIPE_DIRECTION[face.ordinal()] = new ActionPipeDirection(face);
        for (DyeColor colour : DyeColor.values()) {
            ACTION_PIPE_COLOUR[colour.getId()] = new ActionPipeColor(colour);
            TRIGGER_PIPE_SIGNAL[colour.getId() * 2] = new TriggerPipeSignal(true, colour);
            TRIGGER_PIPE_SIGNAL[colour.getId() * 2 + 1] = new TriggerPipeSignal(false, colour);
            ACTION_PIPE_SIGNAL[colour.getId()] = new ActionPipeSignal(colour);
        }
        for (EmzuliBehaviour.SlotIndex slot : EmzuliBehaviour.SlotIndex.VALUES) {
            ACTION_EXTRACTION_PRESET[slot.ordinal()] = new ActionExtractionPreset(slot);
        }
        int index = 0;
        for (int shift = LimiterBehaviour.MAX_SHIFT; shift >= 0; shift--) {
            ACTION_IRON_POWER_LIMIT[index] = new ActionPowerLimit("iron", BCTransport.IRON_POWER, shift, ACTION_IRON_POWER_LIMIT);
            ACTION_DIAMOND_POWER_LIMIT[index] = new ActionPowerLimit("diamond", BCTransport.DIAMOND_POWER, shift, ACTION_DIAMOND_POWER_LIMIT);
            index++;
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
