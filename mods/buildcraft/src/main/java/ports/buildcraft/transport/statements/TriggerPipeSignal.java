package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.transport.wire.PipeWires;

/**
 * Upstream {@code TriggerPipeSignal}: whether the gate's pipe carries a powered wire of one colour, or none of
 * it is powered. Each parameter adds another colour that must be on or off.
 */
public class TriggerPipeSignal extends BCStatement implements Trigger.Internal {
    private final boolean active;
    private final DyeColor colour;
    private final ResourceLocation sprite;

    TriggerPipeSignal(boolean active, DyeColor colour) {
        super("buildcraft:pipe.wire.input." + colour.getName() + (active ? ".active" : ".inactive"),
                "buildcraft.pipe.wire.input." + colour.getName() + (active ? ".active" : ".inactive"));
        this.active = active;
        this.colour = colour;
        sprite = spriteFor(active, colour);
    }

    /** Upstream {@code BCTransportSprites.getPipeSignal}. */
    static ResourceLocation spriteFor(boolean active, DyeColor colour) {
        return sprite("trigger_pipesignal_" + ColourText.upstreamName(colour) + (active ? "_active" : "_inactive"));
    }

    /** Upstream {@code doesGateHaveColour}: whether the gate's pipe has any wire of that colour. */
    public static boolean doesGateHaveColour(Gate gate, DyeColor colour) {
        return gate.getPipe().wires().hasPartOfColour(colour);
    }

    @Override
    public int maxParameters() {
        return 3;
    }

    @Override
    public StatementParameter createParameter(int index) {
        return TriggerParameterSignal.EMPTY;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.pipe.wire." + (active ? "active" : "inactive"), ColourText.of(colour));
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof Gate gate)) return false;
        PipeWires wires = gate.getPipe().wires();
        if (active != wires.isAnyPowered(colour)) return false;
        for (StatementParameter parameter : parameters) {
            if (parameter instanceof TriggerParameterSignal signal && signal.colour != null && signal.active != wires.isAnyPowered(signal.colour)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Statement[] getPossible() {
        return BCTransportStatements.TRIGGER_PIPE_SIGNAL;
    }
}
