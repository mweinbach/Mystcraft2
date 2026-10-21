package ports.buildcraft.core.statements;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.RedstoneStatementContainer;
import ports.buildcraft.statement.SidedStatementContainer;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/** Upstream {@code TriggerRedstoneInput}: the redstone signal reaching the gate's block, or only its face. */
public class TriggerRedstoneInput extends BCStatement implements Trigger.Internal {
    public final boolean active;
    private final ResourceLocation sprite;

    TriggerRedstoneInput(boolean active) {
        super("buildcraft:redstone.input." + state(active), "buildcraft.redstone.input." + state(active));
        this.active = active;
        this.sprite = sprite("trigger_redstoneinput_" + state(active));
    }

    private static String state(boolean active) {
        return active ? "active" : "inactive";
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.redstone.input." + state(active));
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

    @Override
    public boolean isTriggerActive(StatementContainer container, StatementParameter[] parameters) {
        if (!(container instanceof RedstoneStatementContainer redstone)) return false;
        int level = redstone.getRedstoneInput(null);
        if (parameters.length > 0 && parameters[0] instanceof StatementParamGateSideOnly sideOnly && sideOnly.isSpecific
                && container instanceof SidedStatementContainer sided) {
            level = redstone.getRedstoneInput(sided.getSide());
        }
        return active ? level > 0 : level == 0;
    }

    @Override
    public Statement[] getPossible() {
        return BCCoreStatements.TRIGGER_REDSTONE;
    }

    @Nullable
    @Override
    public <T> T convertTo(Class<T> type) {
        T converted = super.convertTo(type);
        if (converted != null) return converted;
        if (active && type.isInstance(BCCoreStatements.ACTION_REDSTONE)) return type.cast(BCCoreStatements.ACTION_REDSTONE);
        return null;
    }
}
