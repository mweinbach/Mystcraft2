package ports.buildcraft.statement;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Upstream {@code IStatementContainer}: what a statement is evaluated for. */
public interface StatementContainer {
    BlockEntity getTile();

    @Nullable
    BlockEntity getNeighbourTile(Direction side);
}
