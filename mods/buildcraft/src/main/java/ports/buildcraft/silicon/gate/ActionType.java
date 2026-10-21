package ports.buildcraft.silicon.gate;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementType;
import ports.buildcraft.statement.StatementWrapper;

/** Upstream {@code ActionType}: a gate action saved as its tag and the part of the pipe it acts on. */
public final class ActionType extends StatementType<ActionWrapper> {
    public static final ActionType INSTANCE = new ActionType();
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    private ActionType() {
        super(ActionWrapper.class);
    }

    @Nullable
    @Override
    public ActionWrapper convertToType(Object value) {
        return value instanceof Action.Internal action ? new ActionWrapper.Internal(action) : null;
    }

    @Nullable
    @Override
    public ActionWrapper readFromNbt(CompoundTag tag) {
        String kind = tag.getString("kind");
        if (kind.isEmpty()) return null;
        Statement statement = StatementManager.getStatement(kind);
        if (statement instanceof Action) {
            ActionWrapper wrapper = read(statement, tag.getByte("side"));
            if (wrapper != null) return wrapper;
        }
        LOGGER.warn("[gate.action] Couldn't find an action called '{}'! (found {})", kind, statement);
        return null;
    }

    /** {@link ActionWrapper#wrap}, or null where it needs a side and the part is the centre. */
    @Nullable
    public static ActionWrapper read(Statement statement, int part) {
        try {
            return ActionWrapper.wrap(statement, StatementWrapper.partFromIndex(part));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @Override
    public CompoundTag writeToNbt(@Nullable ActionWrapper slot) {
        CompoundTag tag = new CompoundTag();
        if (slot == null) return tag;
        tag.putString("kind", slot.getUniqueTag());
        tag.putByte("side", (byte) StatementWrapper.partIndex(slot.sourcePart));
        return tag;
    }
}
