package ports.buildcraft.transport.behaviour;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.EmzuliPipeMenu;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.statements.ActionExtractionPreset;
import ports.buildcraft.transport.statements.BCTransportStatements;

/**
 * Upstream {@code PipeBehaviourEmzuli}: a wooden pipe with four phantom filter slots, each with an optional
 * paint colour. A filter slot takes part only while a gate's matching {@link ActionExtractionPreset} is active
 * (and for two ticks after); extraction steps through the active, non-empty slots in order, pulling items that
 * match the current slot and sending them out painted in that slot's colour.
 */
public class EmzuliBehaviour extends WoodBehaviour {
    public enum SlotIndex {
        SQUARE(DyeColor.RED),
        CIRCLE(DyeColor.GREEN),
        TRIANGLE(DyeColor.BLUE),
        CROSS(DyeColor.YELLOW);

        public static final SlotIndex[] VALUES = values();

        /** The colour of the slot's extraction preset action. */
        public final DyeColor colour;

        SlotIndex(DyeColor colour) {
            this.colour = colour;
        }

        public SlotIndex next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    public final SimpleContainer filters = new SimpleContainer(SlotIndex.VALUES.length) {
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
    private final Map<SlotIndex, DyeColor> slotColours = new EnumMap<>(SlotIndex.class);
    private final EnumSet<SlotIndex> activeSlots = EnumSet.noneOf(SlotIndex.class);
    private final byte[] activatedTtl = new byte[SlotIndex.VALUES.length];
    @Nullable
    private SlotIndex currentSlot;

    public EmzuliBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Nullable
    public DyeColor slotColour(SlotIndex index) {
        return slotColours.get(index);
    }

    public void setSlotColour(SlotIndex index, @Nullable DyeColor colour) {
        if (colour == null) slotColours.remove(index);
        else slotColours.put(index, colour);
        pipe.setChanged();
    }

    public Set<SlotIndex> activeSlots() {
        return Collections.unmodifiableSet(activeSlots);
    }

    @Nullable
    public SlotIndex currentSlot() {
        return currentSlot;
    }

    @Override
    protected int extractItems(ItemFlow flow, Direction dir, int count, boolean simulate) {
        if (currentSlot == null && !activeSlots.isEmpty()) currentSlot = nextSlot();
        if (currentSlot == null) return 0;
        ItemStack filter = filters.getItem(currentSlot.ordinal());
        int extracted = flow.tryExtractItems(count, dir, slotColours.get(currentSlot),
                stack -> !filter.isEmpty() && ItemStack.isSameItemSameTags(filter, stack), simulate);
        if (extracted > 0 && !simulate) {
            currentSlot = nextSlot();
            pipe.setChanged();
        }
        return extracted;
    }

    @Override
    public void onTick() {
        super.onTick();
        for (SlotIndex index : SlotIndex.VALUES) {
            byte ttl = activatedTtl[index.ordinal()];
            if (ttl > 0) {
                ttl--;
                activatedTtl[index.ordinal()] = ttl;
            }
            if (ttl == 0) {
                activeSlots.remove(index);
                if (currentSlot == index) currentSlot = nextSlot();
            }
        }
    }

    /** Upstream {@code getNextSlot}: the next active slot with a filter after the current one, starting from the first. */
    @Nullable
    private SlotIndex nextSlot() {
        SlotIndex current = currentSlot == null ? SlotIndex.CROSS : currentSlot;
        for (int i = 0; i < SlotIndex.VALUES.length; i++) {
            current = current.next();
            if (activeSlots.contains(current) && !filters.getItem(current.ordinal()).isEmpty()) return current;
        }
        return null;
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        if (Wrenches.isWrench(player.getItemInHand(hand))) return super.onUse(player, hand, part);
        Platform.openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new EmzuliPipeMenu(id, inventory, pipe.getBlockPos(), this),
                pipe.getBlockState().getBlock().getName()), pipe.getBlockPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void addInternalActions(Collection<Action.Internal> actions) {
        super.addInternalActions(actions);
        Collections.addAll(actions, BCTransportStatements.ACTION_EXTRACTION_PRESET);
    }

    @Override
    public void onActionActivate(Action action, StatementParameter[] parameters) {
        super.onActionActivate(action, parameters);
        if (action instanceof ActionExtractionPreset preset) {
            activeSlots.add(preset.index);
            activatedTtl[preset.index.ordinal()] = 2;
        }
    }

    @Override
    public void save(CompoundTag tag) {
        super.save(tag);
        ListTag list = new ListTag();
        for (int i = 0; i < filters.getContainerSize(); i++) {
            ItemStack stack = filters.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        tag.put("Filters", list);
        byte active = 0;
        for (SlotIndex index : activeSlots) active |= (byte) (1 << index.ordinal());
        tag.putByte("activeSlots", active);
        tag.putByte("currentSlot", (byte) (currentSlot == null ? -1 : currentSlot.ordinal()));
        for (SlotIndex index : SlotIndex.VALUES) {
            DyeColor colour = slotColours.get(index);
            tag.putByte("slotColors[" + index.ordinal() + "]", (byte) (colour == null ? 0 : colour.getId() + 1));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag list = tag.getList("Filters", Tag.TAG_COMPOUND);
        for (int i = 0; i < filters.getContainerSize(); i++) filters.setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < filters.getContainerSize()) filters.setItem(slot, ItemStack.of(entry));
        }
        activeSlots.clear();
        int active = tag.getByte("activeSlots");
        for (SlotIndex index : SlotIndex.VALUES) {
            if ((active & (1 << index.ordinal())) != 0) activeSlots.add(index);
        }
        int current = tag.contains("currentSlot") ? tag.getByte("currentSlot") : -1;
        currentSlot = current >= 0 && current < SlotIndex.VALUES.length ? SlotIndex.VALUES[current] : null;
        slotColours.clear();
        for (SlotIndex index : SlotIndex.VALUES) {
            int colour = tag.getByte("slotColors[" + index.ordinal() + "]");
            if (colour > 0 && colour <= 16) slotColours.put(index, DyeColor.byId(colour - 1));
        }
    }
}
