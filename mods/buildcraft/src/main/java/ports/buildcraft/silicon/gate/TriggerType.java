package ports.buildcraft.silicon.gate;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementType;
import ports.buildcraft.statement.StatementWrapper;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.statement.TriggerWrapper;

/** Upstream {@code TriggerType}: a gate trigger saved as its tag and the part of the pipe it reads. */
public final class TriggerType extends StatementType<TriggerWrapper> {
    public static final TriggerType INSTANCE = new TriggerType();
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

    private TriggerType() {
        super(TriggerWrapper.class);
    }

    @Nullable
    @Override
    public TriggerWrapper convertToType(Object value) {
        return value instanceof Trigger.Internal trigger ? new TriggerWrapper.Internal(trigger) : null;
    }

    @Nullable
    @Override
    public TriggerWrapper readFromNbt(CompoundTag tag) {
        String kind = tag.getString("kind");
        if (kind.isEmpty()) return null;
        Statement statement = StatementManager.getStatement(kind);
        if (statement instanceof Trigger) {
            TriggerWrapper wrapper = read(statement, tag.getByte("side"));
            if (wrapper != null) return wrapper;
        }
        LOGGER.warn("[gate.trigger] Couldn't find a trigger called '{}'! (found {})", kind, statement);
        return null;
    }

    /** {@link TriggerWrapper#wrap}, or null where it needs a side and the part is the centre. */
    @Nullable
    public static TriggerWrapper read(Statement statement, int part) {
        try {
            return TriggerWrapper.wrap(statement, StatementWrapper.partFromIndex(part));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @Override
    public CompoundTag writeToNbt(@Nullable TriggerWrapper slot) {
        CompoundTag tag = new CompoundTag();
        if (slot == null) return tag;
        tag.putString("kind", slot.getUniqueTag());
        tag.putByte("side", (byte) StatementWrapper.partIndex(slot.sourcePart));
        return tag;
    }
}
