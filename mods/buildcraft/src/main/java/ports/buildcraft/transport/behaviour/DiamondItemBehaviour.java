package ports.buildcraft.transport.behaviour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.DiamondPipeMenu;
import ports.buildcraft.transport.ItemEntry;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;

/**
 * Upstream {@code PipeBehaviourDiamondItem}: nine phantom filter slots per face. Matching items
 * prefer filtered faces, other faces with filters reject them, and stacks are split between
 * matching faces in proportion to the filter stack sizes.
 */
public class DiamondItemBehaviour extends PipeBehaviour {
    public static final int FILTERS_PER_SIDE = 9;

    public final SimpleContainer filters = new SimpleContainer(FILTERS_PER_SIDE * 6) {
        @Override
        public void setChanged() {
            super.setChanged();
            pipe.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !pipe.isRemoved();
        }
    };

    public DiamondItemBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        Platform.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new DiamondPipeMenu(id, inventory, pipe.getBlockPos(), filters),
                pipe.getBlockState().getBlock().getName()), pipe.getBlockPos());
        return InteractionResult.SUCCESS;
    }

    private static boolean matches(ItemStack filter, ItemStack stack) {
        return ItemStack.isSameItemSameTags(filter, stack);
    }

    @Override
    public void itemSideCheck(SideCheck check) {
        for (Direction face : Direction.values()) {
            if (!check.isAllowed(face) || !pipe.isConnected(face)) continue;
            int offset = FILTERS_PER_SIDE * face.ordinal();
            boolean sideAllowed = false;
            boolean foundItem = false;
            for (int i = 0; i < FILTERS_PER_SIDE; i++) {
                ItemStack compareTo = filters.getItem(offset + i);
                if (compareTo.isEmpty()) continue;
                foundItem = true;
                if (matches(compareTo, check.stack)) {
                    sideAllowed = true;
                    break;
                }
            }
            if (foundItem) {
                if (sideAllowed) check.increasePriority(face, 12);
                else check.disallow(face);
            }
        }
    }

    /** Splits only between the faces of highest priority, so a lens filter's preference holds. */
    @Override
    public void split(List<EnumSet<Direction>> order, List<ItemEntry> items, RandomSource random) {
        if (order.isEmpty()) return;
        Direction[] allSides = order.get(0).toArray(new Direction[0]);
        if (allSides.length <= 1) return;
        List<ItemEntry> toSplit = new ArrayList<>(items);
        items.clear();
        for (ItemEntry item : toSplit) {
            int[] countPerSide = new int[allSides.length];
            int totalCount = 0;
            for (int s = 0; s < allSides.length; s++) {
                int offset = FILTERS_PER_SIDE * allSides[s].ordinal();
                for (int i = 0; i < FILTERS_PER_SIDE; i++) {
                    ItemStack compareTo = filters.getItem(offset + i);
                    if (compareTo.isEmpty() || !matches(compareTo, item.stack)) continue;
                    totalCount += compareTo.getCount();
                    countPerSide[s] += compareTo.getCount();
                }
            }
            if (totalCount == 0) {
                totalCount = allSides.length;
                Arrays.fill(countPerSide, 1);
            } else {
                int hcf = countPerSide[0];
                for (int c : countPerSide) hcf = highestCommonFactor(hcf, c);
                if (hcf > 1) {
                    totalCount /= hcf;
                    for (int i = 0; i < countPerSide.length; i++) countPerSide[i] /= hcf;
                }
            }
            ItemEntry[] entries = new ItemEntry[allSides.length];
            ItemStack stack = item.stack.copy();
            if (stack.getCount() >= totalCount) {
                int leftOver = stack.getCount() % totalCount;
                int multiples = (stack.getCount() - leftOver) / totalCount;
                for (int s = 0; s < allSides.length; s++) {
                    ItemStack toSide = stack.copy();
                    toSide.setCount(countPerSide[s] * multiples);
                    entries[s] = new ItemEntry(toSide, item.from);
                    entries[s].to = List.of(allSides[s]);
                }
                stack.setCount(leftOver);
            }
            if (!stack.isEmpty()) {
                int[] randLookup = new int[totalCount];
                int j = 0;
                for (int s = 0; s < allSides.length; s++) {
                    Arrays.fill(randLookup, j, j + countPerSide[s], s);
                    j += countPerSide[s];
                }
                while (!stack.isEmpty()) {
                    int face = randLookup[random.nextInt(totalCount)];
                    if (entries[face] == null) {
                        ItemStack single = stack.copy();
                        single.setCount(1);
                        entries[face] = new ItemEntry(single, item.from);
                        entries[face].to = List.of(allSides[face]);
                    } else {
                        entries[face].stack.grow(1);
                    }
                    stack.shrink(1);
                }
            }
            for (ItemEntry entry : entries) {
                if (entry != null) items.add(entry);
            }
        }
    }

    private static int highestCommonFactor(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return Math.abs(a);
    }

    @Override
    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int i = 0; i < filters.getContainerSize(); i++) {
            ItemStack stack = filters.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        tag.put("filters", list);
    }

    @Override
    public void load(CompoundTag tag) {
        ListTag list = tag.getList("filters", Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.getContainerSize(); i++) filters.setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < filters.getContainerSize()) filters.setItem(slot, ItemStack.of(entry));
        }
    }
}
