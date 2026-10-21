package ports.buildcraft.statement;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/** Upstream {@code IRedstoneStatementContainer}. */
public interface RedstoneStatementContainer {
    /** @param side the face to read, or null for the strongest signal into the block */
    int getRedstoneInput(@Nullable Direction side);

    /** @param side the face to emit from, or null for every face */
    boolean setRedstoneOutput(@Nullable Direction side, int value);
}
