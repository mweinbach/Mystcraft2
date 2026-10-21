package ports.buildcraft.silicon.statement;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.silicon.plug.PluggablePulsar;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.pluggable.PipePluggable;

/** Upstream {@code ActionPowerPulsar}: holds the pulsar on this face on, or queues a single pulse. */
public class ActionPowerPulsar extends BCStatement implements Action.InternalSided, Action.Single {
    public final boolean constant;
    private final ResourceLocation sprite;

    public ActionPowerPulsar(boolean constant) {
        super("buildcraft:pulsar." + (constant ? "constant" : "single"),
                "buildcraft.pulsar.constant" + (constant ? "constant" : "single"));
        this.constant = constant;
        this.sprite = sprite(constant ? "action_pulsar_on" : "action_pulsar_single");
    }

    @Override
    public Component getDescription() {
        return Component.translatable(constant ? "gate.action.pulsar.constant" : "gate.action.pulsar.single");
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public void actionActivate(Direction side, StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof Gate gate)) return;
        PipePluggable plug = gate.getPipe().getPluggable(side);
        if (!(plug instanceof PluggablePulsar pulsar)) return;
        if (constant) pulsar.enablePulsar();
        else pulsar.addSinglePulse();
    }

    @Override
    public boolean singleActionTick() {
        return !constant;
    }

    @Override
    public ActionPowerPulsar[] getPossible() {
        return BCSiliconStatements.ACTION_PULSAR;
    }
}
