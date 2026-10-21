package ports.buildcraft.transport.statements;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementParameterItemStack;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.transport.flow.ItemFlow;

/** Upstream {@code TriggerItemsTraversing}: items, or one kind of item, moving through the gate's pipe. */
public class TriggerItemsTraversing extends BCStatement implements Trigger.Internal {
    private static final ResourceLocation SPRITE = BuildCraft.id("textures/triggers/trigger_pipecontents_containsitems.png");

    TriggerItemsTraversing() {
        super("buildcraft:pipe_contains_items");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.pipe.containsItems");
    }

    @Override
    public ResourceLocation getSprite() {
        return SPRITE;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return StatementParameterItemStack.EMPTY;
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof Gate gate) || !(gate.getPipe().flow() instanceof ItemFlow flow)) return false;
        return flow.containsItemMatching(getParam(0, parameters, StatementParameterItemStack.EMPTY).getItemStack());
    }
}
