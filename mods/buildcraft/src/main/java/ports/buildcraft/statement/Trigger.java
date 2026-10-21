package ports.buildcraft.statement;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code ITrigger} and its internal, internal sided and external forms. */
public interface Trigger extends Statement {
    /** Upstream {@code ITriggerInternal}: reads the gate's own block. */
    interface Internal extends Trigger {
        boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters);
    }

    /** Upstream {@code ITriggerInternalSided}: reads one face of the gate's own block. */
    interface InternalSided extends Trigger {
        boolean isTriggerActive(Direction side, StatementContainer source, StatementParameter[] parameters);
    }

    /** Upstream {@code ITriggerExternal}: reads the block on {@code side} of the gate's block. */
    interface External extends Trigger {
        boolean isTriggerActive(BlockEntity target, Direction side, StatementContainer source, StatementParameter[] parameters);
    }
}
