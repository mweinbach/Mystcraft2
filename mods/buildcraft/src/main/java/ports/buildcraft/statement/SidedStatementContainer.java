package ports.buildcraft.statement;

import net.minecraft.core.Direction;

/** Upstream {@code ISidedStatementContainer}: a container on one face of its block. */
public interface SidedStatementContainer extends StatementContainer {
    Direction getSide();
}
