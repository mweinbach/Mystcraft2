package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.transport.flow.PowerFlow;

/** Upstream {@code TriggerPowerRequested}: something connected to the gate's power pipe is asking for power. */
public class TriggerPowerRequested extends BCStatement implements Trigger.Internal {
    private static final ResourceLocation SPRITE = BuildCraft.id("textures/triggers/trigger_pipecontents_requestsenergy.png");

    TriggerPowerRequested() {
        super("buildcraft:powerRequested");
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        return source instanceof Gate gate && gate.getPipe().flow() instanceof PowerFlow flow && flow.getPowerRequested(null) > 0;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.pipe.requestsEnergy");
    }

    @Override
    public ResourceLocation getSprite() {
        return SPRITE;
    }
}
