package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.transport.flow.FluidFlow;

/** Upstream {@code TriggerFluidsTraversing}: fluid anywhere in the gate's pipe. */
public class TriggerFluidsTraversing extends BCStatement implements Trigger.Internal {
    private static final ResourceLocation SPRITE = BuildCraft.id("textures/triggers/trigger_pipecontents_containsfluids.png");

    TriggerFluidsTraversing() {
        super("buildcraft:pipe_contains_fluids");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.pipe.containsFluids");
    }

    @Override
    public ResourceLocation getSprite() {
        return SPRITE;
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        return source instanceof Gate gate && gate.getPipe().flow() instanceof FluidFlow flow && flow.totalAmount() > 0;
    }
}
