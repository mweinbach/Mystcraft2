package ports.buildcraft.statement;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code TriggerWrapper}: a trigger placed in a gate, evaluated as an internal trigger. */
public abstract class TriggerWrapper extends StatementWrapper implements Trigger.Internal {
    protected TriggerWrapper(Statement delegate, @Nullable Direction sourcePart) {
        super(delegate, sourcePart);
    }

    /** Internal triggers need no side; sided and external triggers need one. */
    @Nullable
    public static TriggerWrapper wrap(@Nullable Statement statement, @Nullable Direction side) {
        if (statement == null) return null;
        if (statement instanceof TriggerWrapper wrapper) return wrapper;
        if (statement instanceof Trigger.Internal internal && side == null) return new Internal(internal);
        if (statement instanceof Trigger.InternalSided sided) {
            if (side == null) throw new NullPointerException("side");
            return new InternalSided(sided, side);
        }
        if (statement instanceof Trigger.External external) {
            if (side == null) throw new NullPointerException("side");
            return new External(external, side);
        }
        throw new IllegalArgumentException("Unknown class or interface " + statement.getClass());
    }

    /** The delegate's ring, then the delegate on each of the other five parts when it reads a face. */
    @Override
    public TriggerWrapper[] getPossible() {
        Statement[] possible = delegate.getPossible();
        boolean andSides = sourcePart != null;
        TriggerWrapper[] real = new TriggerWrapper[possible.length + (andSides ? 5 : 0)];
        for (int i = 0; i < possible.length; i++) real[i] = wrap(possible[i], sourcePart);
        if (andSides) {
            Direction part = sourcePart;
            for (int j = 0; j < 5; j++) {
                part = nextPart(part);
                real[possible.length + j] = wrap(delegate, part);
            }
        }
        return real;
    }

    public static final class Internal extends TriggerWrapper {
        public final Trigger.Internal trigger;

        public Internal(Trigger.Internal trigger) {
            super(trigger, null);
            this.trigger = trigger;
        }

        @Override
        public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
            return trigger.isTriggerActive(source, parameters);
        }
    }

    public static final class InternalSided extends TriggerWrapper {
        public final Trigger.InternalSided trigger;

        public InternalSided(Trigger.InternalSided trigger, Direction side) {
            super(trigger, side);
            this.trigger = trigger;
        }

        @Override
        public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
            return trigger.isTriggerActive(sourcePart, source, parameters);
        }
    }

    /** Upstream's {@code ITriggerExternalOverride} has no implementers, so it is not ported. */
    public static final class External extends TriggerWrapper {
        public final Trigger.External trigger;

        public External(Trigger.External trigger, Direction side) {
            super(trigger, side);
            this.trigger = trigger;
        }

        @Override
        public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
            BlockEntity tile = getNeighbourTile(source);
            return tile != null && trigger.isTriggerActive(tile, sourcePart, source, parameters);
        }
    }
}
