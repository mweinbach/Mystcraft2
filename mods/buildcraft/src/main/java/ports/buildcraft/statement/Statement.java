package ports.buildcraft.statement;

import javax.annotation.Nullable;

/** Upstream {@code IStatement}: a trigger or action, with up to {@link #maxParameters()} parameters. */
public interface Statement extends GuiSlot {
    int maxParameters();

    int minParameters();

    /** The default parameter in slot {@code index}, or null if that slot takes none. */
    @Nullable
    StatementParameter createParameter(int index);

    /** Keeps {@code old} when it is the same kind of parameter as the default, otherwise the default. */
    @Nullable
    default StatementParameter createParameter(@Nullable StatementParameter old, int index) {
        StatementParameter created = createParameter(index);
        if (old == null || created == null) return created;
        return old.getClass() == created.getClass() ? old : created;
    }

    Statement rotateLeft();

    /** The statements offered in this one's ring of choices in the gate GUI. */
    Statement[] getPossible();

    default boolean isPossibleOrdered() {
        return false;
    }
}
