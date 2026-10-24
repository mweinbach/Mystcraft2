package ports.gridwright.transfer;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a filter item lets through. It lists up to nine items; as a whitelist only listed items pass, as
 * a blacklist everything else does. With NBT matching on, a listed item must also carry the same NBT.
 * For fluid nodes an entry stands for the fluid its container holds (a water bucket lists water).
 */
public final class ItemFilter {

    public static final int SIZE = 9;
    /** Lets everything through: what a node without a filter uses. */
    public static final ItemFilter NONE = new ItemFilter(List.of(), true, false);

    private static final String TAG = "Filter";
    private static final String ITEMS = "Items";
    private static final String BLACKLIST = "Blacklist";
    private static final String MATCH_NBT = "MatchNbt";

    private final List<ItemStack> entries;
    private final boolean blacklist;
    private final boolean matchNbt;
    @Nullable private List<Fluid> fluids;

    private ItemFilter(List<ItemStack> entries, boolean blacklist, boolean matchNbt) {
        this.entries = entries;
        this.blacklist = blacklist;
        this.matchNbt = matchNbt;
    }

    /** The filter a filter item describes, or {@link #NONE} for any other stack. */
    public static ItemFilter of(ItemStack stack) {
        if (!(stack.getItem() instanceof FilterItem)) return NONE;
        NonNullList<ItemStack> slots = readSlots(stack);
        List<ItemStack> entries = new ArrayList<>();
        for (ItemStack entry : slots) if (!entry.isEmpty()) entries.add(entry);
        CompoundTag tag = stack.getTagElement(TAG);
        return new ItemFilter(List.copyOf(entries), tag != null && tag.getBoolean(BLACKLIST), tag != null && tag.getBoolean(MATCH_NBT));
    }

    public boolean blacklist() {
        return blacklist;
    }

    public boolean matchNbt() {
        return matchNbt;
    }

    public int size() {
        return entries.size();
    }

    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean listed = false;
        for (ItemStack entry : entries) {
            if (entry.is(stack.getItem()) && (!matchNbt || Objects.equals(entry.getTag(), stack.getTag()))) {
                listed = true;
                break;
            }
        }
        return listed != blacklist;
    }

    public boolean testFluid(Fluid fluid) {
        if (fluids == null) {
            List<Fluid> found = new ArrayList<>();
            for (ItemStack entry : entries) {
                Fluid held = TransferPlatform.fluidIn(entry);
                if (held != null) found.add(held);
            }
            fluids = List.copyOf(found);
        }
        boolean listed = false;
        for (Fluid entry : fluids) {
            if (entry.isSame(fluid)) {
                listed = true;
                break;
            }
        }
        return listed != blacklist;
    }

    /** The nine slots of a filter item, empty where nothing is listed. */
    public static NonNullList<ItemStack> readSlots(ItemStack filter) {
        NonNullList<ItemStack> slots = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        CompoundTag tag = filter.getTagElement(TAG);
        if (tag == null) return slots;
        ListTag list = tag.getList(ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < SIZE) slots.set(slot, ItemStack.of(entry));
        }
        return slots;
    }

    /** Writes {@code slots} and the two switches into {@code filter}'s NBT. */
    public static void write(ItemStack filter, List<ItemStack> slots, boolean blacklist, boolean matchNbt) {
        CompoundTag tag = filter.getOrCreateTagElement(TAG);
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(SIZE, slots.size()); i++) {
            ItemStack entry = slots.get(i);
            if (entry.isEmpty()) continue;
            CompoundTag saved = new CompoundTag();
            saved.putByte("Slot", (byte) i);
            entry.copy().split(1).save(saved);
            list.add(saved);
        }
        tag.put(ITEMS, list);
        tag.putBoolean(BLACKLIST, blacklist);
        tag.putBoolean(MATCH_NBT, matchNbt);
    }
}
