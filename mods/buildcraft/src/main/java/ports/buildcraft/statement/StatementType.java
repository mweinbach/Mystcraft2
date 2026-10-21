package ports.buildcraft.statement;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/** Upstream {@code StatementType}: how one kind of statement is saved, and what other values convert into it. */
public abstract class StatementType<S extends GuiSlot> {
    public final Class<S> clazz;

    protected StatementType(Class<S> clazz) {
        this.clazz = clazz;
    }

    @Nullable
    public abstract S readFromNbt(CompoundTag tag);

    public abstract CompoundTag writeToNbt(@Nullable S slot);

    @Nullable
    public abstract S convertToType(Object value);
}
