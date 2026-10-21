package ports.buildcraft.core.statements;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementParameterItemStack;
import ports.buildcraft.statement.Trigger;

/**
 * Upstream {@code TriggerFluidContainer}: whether the neighbouring tank is empty, holds a fluid, has room for one, or is full.
 * Upstream called a tank empty when nothing could be drained from it, so a fill-only face holding fluid read as both
 * empty and holding fluid; emptiness here reads the same contents as {@link State#CONTAINS}.
 */
public class TriggerFluidContainer extends BCStatement implements Trigger.External {
    public enum State {
        EMPTY,
        CONTAINS,
        SPACE,
        FULL
    }

    public final State state;
    private final ResourceLocation sprite;

    TriggerFluidContainer(State state) {
        super("buildcraft:fluid." + name(state), "buildcraft.fluid." + name(state));
        this.state = state;
        this.sprite = sprite("trigger_liquidcontainer_" + name(state));
    }

    private static String name(State state) {
        return state.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public int maxParameters() {
        return state == State.CONTAINS || state == State.SPACE ? 1 : 0;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.fluid." + name(state));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, StatementContainer container, StatementParameter[] parameters) {
        FluidPort port = BCCoreStatements.fluids(tile, side);
        if (port == null) return false;
        Fluid searched = BCCoreStatements.searchedFluid(parameters);
        List<FluidAmount> tanks = port.contents();
        if (tanks.isEmpty()) return false;
        return switch (state) {
            case EMPTY -> {
                for (FluidAmount fluid : tanks) {
                    if (!fluid.isEmpty()) yield false;
                }
                yield true;
            }
            case CONTAINS -> {
                for (FluidAmount fluid : tanks) {
                    if (!fluid.isEmpty() && (searched == null || fluid.fluid() == searched)) yield true;
                }
                yield false;
            }
            case SPACE -> searched == null ? anyTankHasRoom(port, tanks) : port.fill(searched, 1, true) > 0;
            case FULL -> searched == null ? !anyTankHasRoom(port, tanks) : port.fill(searched, 1, true) <= 0;
        };
    }

    private static boolean anyTankHasRoom(FluidPort port, List<FluidAmount> tanks) {
        for (int i = 0; i < tanks.size(); i++) {
            if (tanks.get(i).isEmpty() || tanks.get(i).amount() < port.capacity(i)) return true;
        }
        return false;
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return StatementParameterItemStack.EMPTY;
    }

    @Override
    public Statement[] getPossible() {
        return BCCoreStatements.TRIGGER_FLUID_ALL;
    }
}
