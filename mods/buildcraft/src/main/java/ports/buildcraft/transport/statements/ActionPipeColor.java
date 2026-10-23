package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/**
 * Upstream {@code ActionPipeColor}: sets the colour of a lapis or daizuli pipe; the pipe's behaviour listens for it.
 * Its icon is the paintbrush of that colour, as upstream.
 */
public class ActionPipeColor extends BCStatement implements Action.Internal {
    public final DyeColor colour;
    private final ResourceLocation sprite;

    ActionPipeColor(DyeColor colour) {
        super("buildcraft:pipe.color." + colour.getName(), "buildcraft.pipe." + colour.getName());
        this.colour = colour;
        sprite = BuildCraft.id("textures/item/paintbrush/" + colour.getName() + ".png");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.pipe.item.color", ColourText.of(colour));
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {}

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Statement[] getPossible() {
        return BCTransportStatements.ACTION_PIPE_COLOUR;
    }

    @Override
    public String toString() {
        return "ActionPipeColor[" + colour + "]";
    }
}
