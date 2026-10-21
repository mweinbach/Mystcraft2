package ports.buildcraft.core.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/** Upstream {@code TriggerTrue}: always active. */
public class TriggerTrue extends BCStatement implements Trigger.Internal {
    private static final ResourceLocation SPRITE = sprite("trigger_true");

    TriggerTrue() {
        super("buildcraftcore:trigger.true");
    }

    @Override
    public ResourceLocation getSprite() {
        return SPRITE;
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        return true;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.true");
    }
}
