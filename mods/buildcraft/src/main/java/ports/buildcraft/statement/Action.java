package ports.buildcraft.statement;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code IAction} and its internal, internal sided, external and single forms. */
public interface Action extends Statement {
    /** Upstream {@code IActionInternal}: acts on the gate's own block. */
    interface Internal extends Action {
        void actionActivate(StatementContainer source, StatementParameter[] parameters);
    }

    /** Upstream {@code IActionInternalSided}: acts on one face of the gate's own block. */
    interface InternalSided extends Action {
        void actionActivate(Direction side, StatementContainer source, StatementParameter[] parameters);
    }

    /** Upstream {@code IActionExternal}: acts on the block on {@code side} of the gate's block. */
    interface External extends Action {
        void actionActivate(BlockEntity target, Direction side, StatementContainer source, StatementParameter[] parameters);
    }

    /** Upstream {@code IActionSingle}: while it stays active, it is only activated again when this returns false. */
    interface Single extends Action {
        boolean singleActionTick();
    }
}
