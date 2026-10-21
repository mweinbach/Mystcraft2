package ports.buildcraft.core.statements;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.RedstoneStatementContainer;
import ports.buildcraft.statement.SidedStatementContainer;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/** Upstream {@code ActionRedstoneOutput}: a full-strength signal out of every face of the gate's block, or only its own. */
public class ActionRedstoneOutput extends BCStatement implements Action.Internal {
    private static final ResourceLocation SPRITE = sprite("action_redstoneoutput");

    ActionRedstoneOutput() {
        super("buildcraft:redstone.output", "buildcraft.redstone.output");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.redstone.signal");
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return index == 0 ? StatementParamGateSideOnly.ANY : null;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    protected boolean isSideOnly(@Nullable StatementParameter[] parameters) {
        return parameters != null && parameters.length > 0 && parameters[0] instanceof StatementParamGateSideOnly sideOnly && sideOnly.isSpecific;
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
        if (!(source instanceof RedstoneStatementContainer redstone)) return;
        Direction side = null;
        if (source instanceof SidedStatementContainer sided && isSideOnly(parameters)) side = sided.getSide();
        redstone.setRedstoneOutput(side, getSignalLevel(parameters));
    }

    protected int getSignalLevel(StatementParameter[] parameters) {
        return 15;
    }

    @Override
    public ResourceLocation getSprite() {
        return SPRITE;
    }

    @Nullable
    @Override
    public <T> T convertTo(Class<T> type) {
        T converted = super.convertTo(type);
        if (converted != null) return converted;
        return type.isInstance(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE) ? type.cast(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE) : null;
    }
}
