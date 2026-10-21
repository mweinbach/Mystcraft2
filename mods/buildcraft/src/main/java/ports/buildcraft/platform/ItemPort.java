package ports.buildcraft.platform;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

/** An item inventory face: Forge item handlers on Forge, the Transfer API on Fabric. */
public interface ItemPort {
    /** @return the part of {@code stack} that did not fit. */
    ItemStack insert(ItemStack stack, boolean simulate);

    /** Extracts up to {@code max} items of the first stack type that matches {@code filter}. */
    ItemStack extract(Predicate<ItemStack> filter, int max, boolean simulate);

    /** Null when the port cannot be rolled back; see {@link EnergyPort#snapshot()}. */
    @Nullable
    default Object snapshot() {
        return null;
    }

    default void restore(Object snapshot) {}

    default void onCommitted() {}

    /**
     * The slots of a real inventory, for gate triggers that inspect contents. Pipes and providing
     * machines, which hold nothing, return an empty list.
     */
    default List<SlotView> slots() {
        return List.of();
    }

    interface SlotView {
        ItemStack stack();

        int limit();

        /** @return the part of {@code stack} that would not fit into this slot. */
        ItemStack simulateInsert(ItemStack stack);
    }
}
