package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Upstream {@code ItemHandlerSimple}'s transactor over a run of a container's slots: inserting fills slots
 * already holding the item before empty ones, and extracting gathers matching stacks that merge together.
 */
public final class SlotRange {
    private final Container container;
    private final int start;
    private final int size;

    public SlotRange(Container container, int start, int size) {
        this.container = container;
        this.start = start;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean contains(int containerSlot) {
        return containerSlot >= start && containerSlot < start + size;
    }

    public ItemStack get(int slot) {
        return container.getItem(start + slot);
    }

    public void set(int slot, ItemStack stack) {
        container.setItem(start + slot, stack);
    }

    public List<ItemStack> stacks() {
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) stacks.add(get(i));
        return stacks;
    }

    /** Upstream {@code insert(stack, false, simulate)}: the part of {@code stack} that did not fit. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack remaining = stack.copy();
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < size; slot++) {
                ItemStack inSlot = get(slot);
                if (inSlot.isEmpty() != (pass == 1)) continue;
                remaining = insert(slot, remaining, simulate);
                if (remaining.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private ItemStack insert(int slot, ItemStack stack, boolean simulate) {
        ItemStack inSlot = get(slot);
        int limit = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
        if (!inSlot.isEmpty() && !ItemStack.isSameItemSameTags(inSlot, stack)) return stack;
        int moved = Math.min(stack.getCount(), limit - inSlot.getCount());
        if (moved <= 0) return stack;
        if (!simulate) {
            ItemStack placed = stack.copy();
            placed.setCount(inSlot.getCount() + moved);
            set(slot, placed);
        }
        ItemStack rest = stack.copy();
        rest.shrink(moved);
        return rest;
    }

    /** Upstream {@code canFullyAccept}: all of {@code stack} would fit. */
    public boolean canFullyAccept(ItemStack stack) {
        return insert(stack, true).isEmpty();
    }

    /**
     * Upstream {@code extract(new ArrayStackFilter(filter), min, max, simulate)}: up to {@code max} items equal to
     * {@code filter} apart from count, or nothing if fewer than {@code min} are there.
     */
    public ItemStack extract(ItemStack filter, int min, int max, boolean simulate) {
        min = Math.max(1, min);
        if (min > max) return ItemStack.EMPTY;
        List<Integer> valid = new ArrayList<>();
        int total = 0;
        for (int slot = 0; slot < size && total < max; slot++) {
            ItemStack inSlot = get(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameTags(inSlot, filter)) continue;
            total += Math.min(inSlot.getCount(), max - total);
            valid.add(slot);
        }
        if (total < min) return ItemStack.EMPTY;
        ItemStack extracted = ItemStack.EMPTY;
        int wanted = total;
        for (int slot : valid) {
            ItemStack inSlot = get(slot);
            int take = Math.min(inSlot.getCount(), wanted);
            wanted -= take;
            if (extracted.isEmpty()) {
                extracted = inSlot.copy();
                extracted.setCount(take);
            } else {
                extracted.grow(take);
            }
            if (!simulate) {
                ItemStack left = inSlot.copy();
                left.shrink(take);
                set(slot, left.isEmpty() ? ItemStack.EMPTY : left);
            }
        }
        return extracted;
    }
}
