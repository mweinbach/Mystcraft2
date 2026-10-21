package ports.buildcraft.core.statements;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.lib.Controllable;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/** Upstream {@code ActionMachineControl}: sets a neighbouring machine's control mode. */
public class ActionMachineControl extends BCStatement implements Action.External {
    public final Controllable.Mode mode;
    private final ResourceLocation sprite;

    public ActionMachineControl(Controllable.Mode mode) {
        super("buildcraft:machine." + mode.lowerCaseName, "buildcraft.machine." + mode.lowerCaseName);
        this.mode = mode;
        this.sprite = sprite("action_machinecontrol_" + mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.machine." + mode.lowerCaseName);
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public void actionActivate(BlockEntity target, Direction side, StatementContainer source, StatementParameter[] parameters) {
        if (target instanceof Controllable controllable && controllable.acceptsControlMode(mode)) {
            controllable.setControlMode(mode);
        }
    }

    @Override
    public ActionMachineControl[] getPossible() {
        return BCCoreStatements.ACTION_MACHINE_CONTROL;
    }
}
