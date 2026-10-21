package ports.buildcraft.transport.statements;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.SideText;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/** Upstream {@code ActionPipeDirection}: turns an iron pipe to face one side; the pipe's behaviour does the turning. */
public class ActionPipeDirection extends BCStatement implements Action.Internal {
    public final Direction direction;
    private final ResourceLocation sprite;

    ActionPipeDirection(Direction direction) {
        super("buildcraft:pipe.dir." + name(direction), "buildcraft.pipe.dir." + name(direction));
        this.direction = direction;
        this.sprite = sprite("trigger_dir_" + name(direction));
    }

    private static String name(Direction direction) {
        return direction.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.pipe.direction", SideText.of(direction));
    }

    @Override
    public Statement rotateLeft() {
        Direction face = direction.getAxis() == Direction.Axis.Y ? direction : direction.getClockWise();
        return BCTransportStatements.ACTION_PIPE_DIRECTION[face.ordinal()];
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {}

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Statement[] getPossible() {
        return BCTransportStatements.ACTION_PIPE_DIRECTION;
    }

    @Override
    public String toString() {
        return "ActionPipeDirection[" + direction + "]";
    }
}
