package ports.buildcraft.statement;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Upstream {@code IStatementParameter}: one parameter slot's value. */
public interface StatementParameter extends GuiSlot {
    enum DrawType {
        SPRITE_ONLY,
        STACK_ONLY,
        STACK_ONLY_OR_QUESTION_MARK,
        SPRITE_STACK,
        SPRITE_STACK_OR_QUESTION_MARK,
        STACK_SPRITE,
        STACK_OR_QUESTION_MARK_THEN_SPRITE
    }

    /** Upstream {@code StatementMouseClick}. */
    record MouseClick(int button, boolean shift) {}

    ItemStack getItemStack();

    default DrawType getDrawType() {
        return DrawType.SPRITE_STACK;
    }

    /**
     * @param stack the stack the player is carrying in the GUI
     * @return the new value, or null to open the ring of {@link #getPossible(StatementContainer)} instead
     */
    @Nullable
    StatementParameter onClick(StatementContainer source, Statement statement, ItemStack stack, MouseClick mouse);

    void writeToNbt(CompoundTag tag);

    StatementParameter rotateLeft();

    @Nullable
    StatementParameter[] getPossible(StatementContainer source);

    default boolean isPossibleOrdered() {
        return false;
    }
}
