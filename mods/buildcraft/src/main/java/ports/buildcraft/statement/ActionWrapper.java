package ports.buildcraft.statement;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code ActionWrapper}: an action placed in a gate, which remembers whether it is active. */
public abstract class ActionWrapper extends StatementWrapper implements Action.Internal {
    protected boolean isActive;

    protected ActionWrapper(Action delegate, @Nullable Direction sourcePart) {
        super(delegate, sourcePart);
    }

    public Action getDelegate() {
        return (Action) delegate;
    }

    /** Internal actions need no side; sided and external actions need one. */
    @Nullable
    public static ActionWrapper wrap(@Nullable Statement statement, @Nullable Direction side) {
        if (statement == null) return null;
        if (statement instanceof ActionWrapper wrapper) return wrapper;
        if (statement instanceof Action.Internal internal && side == null) return new Internal(internal);
        if (statement instanceof Action.InternalSided sided) {
            if (side == null) throw new NullPointerException("side");
            return new InternalSided(sided, side);
        }
        if (statement instanceof Action.External external) {
            if (side == null) throw new NullPointerException("side");
            return new External(external, side);
        }
        throw new IllegalArgumentException("Unknown class or interface " + statement.getClass());
    }

    @Override
    public ActionWrapper[] getPossible() {
        Statement[] possible = delegate.getPossible();
        List<ActionWrapper> list = new ArrayList<>(possible.length + 5);
        for (Statement statement : possible) list.add(wrap(statement, sourcePart));
        if (sourcePart != null) {
            Direction part = sourcePart;
            for (int j = 0; j < 5; j++) {
                part = nextPart(part);
                list.add(wrap(delegate, part));
            }
        }
        return list.toArray(new ActionWrapper[0]);
    }

    public void actionDeactivated(StatementContainer source, StatementParameter[] parameters) {
        isActive = false;
    }

    /** Upstream's {@code IActionSingle} check: a single action that is still active skips this tick. */
    protected boolean skipTick() {
        return isActive && delegate instanceof Action.Single single && single.singleActionTick();
    }

    public static final class Internal extends ActionWrapper {
        public final Action.Internal action;

        public Internal(Action.Internal action) {
            super(action, null);
            this.action = action;
        }

        @Override
        public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
            if (skipTick()) return;
            action.actionActivate(source, parameters);
            isActive = true;
        }
    }

    public static final class InternalSided extends ActionWrapper {
        public final Action.InternalSided action;

        public InternalSided(Action.InternalSided action, Direction side) {
            super(action, side);
            this.action = action;
        }

        @Override
        public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
            if (skipTick()) return;
            action.actionActivate(sourcePart, source, parameters);
            isActive = true;
        }
    }

    /** Upstream's {@code IActionReceptor} has no implementers, so it is not ported. */
    public static final class External extends ActionWrapper {
        public final Action.External action;

        public External(Action.External action, Direction side) {
            super(action, side);
            this.action = action;
        }

        @Override
        public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
            if (skipTick()) return;
            BlockEntity neighbour = getNeighbourTile(source);
            if (neighbour == null) return;
            action.actionActivate(neighbour, sourcePart, source, parameters);
            isActive = true;
        }
    }
}
