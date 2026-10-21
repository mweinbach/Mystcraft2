package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.wire.WireEmitter;

/** Upstream {@code ActionPipeSignal}: powers the gate pipe's wires of one colour, and of each colour in its parameters. */
public class ActionPipeSignal extends BCStatement implements Action.Internal {
    public final DyeColor colour;
    private final ResourceLocation sprite;

    ActionPipeSignal(DyeColor colour) {
        super("buildcraft:pipe.wire.output." + colour.getName(), "buildcraft.pipe.wire.output." + colour.getName());
        this.colour = colour;
        sprite = TriggerPipeSignal.spriteFor(true, colour);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.pipe.wire", ColourText.of(colour));
    }

    @Override
    public int maxParameters() {
        return 3;
    }

    @Override
    public StatementParameter createParameter(int index) {
        return ActionParameterSignal.EMPTY;
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof WireEmitter emitter)) return;
        emitter.emitWire(colour);
        for (StatementParameter parameter : parameters) {
            if (parameter instanceof ActionParameterSignal signal && signal.colour != null) emitter.emitWire(signal.colour);
        }
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Statement[] getPossible() {
        return BCTransportStatements.ACTION_PIPE_SIGNAL;
    }
}
