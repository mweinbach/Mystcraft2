package ports.jurassicraft.machine;

import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.genetics.Hybridization;

/** Server-side processing and sided inventories for each DNA machine. */
public final class DnaMachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private final DnaMachineKind kind;
    private NonNullList<ItemStack> items;
    private final int[] progress = new int[3];
    private boolean hybridizerMode;
    @Nullable private Component customName;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index < 3 ? progress[index] : index == 3 ? kind.ticks : hybridizerMode ? 1 : 0; }
        @Override public void set(int index, int value) { if (index < 3) progress[index] = value; else if (index == 4) hybridizerMode = value != 0; }
        @Override public int getCount() { return 5; }
    };

    public DnaMachineBlockEntity(BlockPos pos, BlockState state) {
        super(DnaMachineRegistry.ENTITY.get(), pos, state);
        kind = ((DnaMachineBlock) state.getBlock()).kind();
        items = NonNullList.withSize(kind.slots, ItemStack.EMPTY);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DnaMachineBlockEntity machine) {
        if (level.isClientSide) return;
        boolean changed = false;
        for (int lane = 0; lane < machine.kind.lanes(); lane++) {
            if (!machine.ready(lane)) {
                if (machine.progress[lane] != 0) { machine.progress[lane] = 0; changed = true; }
                continue;
            }
            machine.progress[lane]++;
            changed = true;
            if (machine.progress[lane] >= machine.kind.ticks) {
                machine.finish(lane, level.random);
                machine.progress[lane] = 0;
            }
        }
        if (changed) machine.setChanged();
    }

    private boolean ready(int lane) {
        if (kind == DnaMachineKind.SYNTHESIZER) {
            if (!items.get(1).is(ComponentsRegistry.EMPTY_TEST_TUBE.get())
                    || !items.get(2).is(ComponentsRegistry.DNA_NUCLEOTIDES.get())) return false;
            ItemStack result = Genetics.synthesize(items.get(0), RandomSource.create());
            return !result.isEmpty() && synthesizerOutput(result) >= 0;
        }
        if (outputSlot(lane) < 0) return false;
        RandomSource preview = RandomSource.create(0);
        return switch (kind) {
            case EXTRACTOR -> isBlankDisc(items.get(1)) && !Genetics.extract(items.get(0), preview).isEmpty();
            case SEQUENCER -> isBlankDisc(items.get(lane * 2 + 1)) && Genetics.canSequence(items.get(lane * 2));
            case COMBINATOR -> !(hybridizerMode ? Hybridization.hybridize(items.subList(3, 11))
                    : Genetics.combine(items.get(0), items.get(1))).isEmpty();
            case SYNTHESIZER -> false; // handled above, including compatible output stacks
        };
    }

    private void finish(int lane, RandomSource random) {
        ItemStack result = switch (kind) {
            case EXTRACTOR -> Genetics.extract(items.get(0), random);
            case SEQUENCER -> Genetics.sequence(items.get(lane * 2), random);
            case COMBINATOR -> hybridizerMode ? Hybridization.hybridize(items.subList(3, 11)) : Genetics.combine(items.get(0), items.get(1));
            case SYNTHESIZER -> Genetics.synthesize(items.get(0), random);
        };
        if (result.isEmpty()) return;
        int output = kind == DnaMachineKind.SYNTHESIZER ? synthesizerOutput(result) : outputSlot(lane);
        if (output < 0) return;
        if (items.get(output).isEmpty()) items.set(output, result);
        else items.get(output).grow(result.getCount());
        switch (kind) {
            case EXTRACTOR -> { items.get(0).shrink(1); items.get(1).shrink(1); }
            case COMBINATOR -> {
                if (hybridizerMode) for (int i = 3; i <= 10; i++) items.get(i).shrink(1);
                else { items.get(0).shrink(1); items.get(1).shrink(1); }
            }
            case SEQUENCER -> { items.get(lane * 2).shrink(1); items.get(lane * 2 + 1).shrink(1); }
            // The disc is retained, just as in the original synthesizer.
            case SYNTHESIZER -> { items.get(1).shrink(1); items.get(2).shrink(1); }
        }
        if (kind == DnaMachineKind.EXTRACTOR || kind == DnaMachineKind.SEQUENCER) syncVisuals();
    }

    private int synthesizerOutput(ItemStack result) {
        for (int slot = kind.inputs; slot < kind.slots; slot++) {
            ItemStack present = items.get(slot);
            if (present.isEmpty() || (ItemStack.isSameItemSameTags(present, result)
                    && present.getCount() + result.getCount() <= Math.min(present.getMaxStackSize(), getMaxStackSize()))) return slot;
        }
        return -1;
    }

    private int outputSlot(int lane) {
        if (kind == DnaMachineKind.SEQUENCER) return items.get(6 + lane).isEmpty() ? 6 + lane : -1;
        if (kind == DnaMachineKind.COMBINATOR) {
            int slot = hybridizerMode ? 11 : 2;
            return items.get(slot).isEmpty() ? slot : -1;
        }
        for (int slot = kind.inputs; slot < kind.slots; slot++) if (items.get(slot).isEmpty()) return slot;
        return -1;
    }

    public static boolean isBlankDisc(ItemStack stack) {
        if (!stack.is(GeneticsRegistry.STORAGE_DISC.get())) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return true;
        for (String key : new String[]{"DNAQuality", "Genetics", "StorageId", "SpeciesId", "Dinosaur", "Plant"}) if (tag.contains(key)) return false;
        return true;
    }
    public static boolean accepts(DnaMachineKind kind, int slot, ItemStack stack) {
        return accepts(kind, slot, stack, false);
    }
    public static boolean accepts(DnaMachineKind kind, int slot, ItemStack stack, boolean hybridizerMode) {
        if (slot < 0 || slot >= kind.slots || stack.isEmpty()) return false;
        if (kind == DnaMachineKind.COMBINATOR) {
            if (hybridizerMode ? slot < 3 || slot > 10 : slot > 1) return false;
            var dna = Genetics.read(stack.getTag());
            return stack.is(GeneticsRegistry.STORAGE_DISC.get()) && dna.isPresent()
                    && (!hybridizerMode || dna.get().kind() == ports.jurassicraft.genetics.DNAData.Kind.DINOSAUR && dna.get().quality() == 100);
        }
        if (slot >= kind.inputs) return false;
        return switch (kind) {
            case EXTRACTOR -> slot == 1 ? isBlankDisc(stack) : !Genetics.extract(stack, RandomSource.create(0)).isEmpty();
            case SEQUENCER -> (slot % 2 == 1) ? isBlankDisc(stack) : Genetics.canSequence(stack);
            case COMBINATOR -> false; // handled above
            case SYNTHESIZER -> slot == 0 ? Genetics.canSynthesize(stack)
                    : slot == 1 ? stack.is(ComponentsRegistry.EMPTY_TEST_TUBE.get()) : stack.is(ComponentsRegistry.DNA_NUCLEOTIDES.get());
        };
    }

    @Override public int getContainerSize() { return kind.slots; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) { resetFor(slot); setChanged(); if (visualSlot(slot)) syncVisuals(); }
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack result = ContainerHelper.takeItem(items, slot); resetFor(slot); if (!result.isEmpty() && visualSlot(slot)) syncVisuals(); return result; }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        resetFor(slot);
        setChanged();
        if (visualSlot(slot)) syncVisuals();
    }
    private void resetFor(int slot) {
        if (slot < kind.inputs || kind == DnaMachineKind.COMBINATOR && slot >= 3 && slot <= 10)
            progress[kind == DnaMachineKind.SEQUENCER ? slot / 2 : 0] = 0;
    }
    @Override public void clearContent() { items.clear(); java.util.Arrays.fill(progress, 0); setChanged(); syncVisuals(); }

    private boolean visualSlot(int slot) {
        return kind == DnaMachineKind.EXTRACTOR && slot == 0
                || kind == DnaMachineKind.SEQUENCER && (slot == 0 || slot == 2 || slot == 4);
    }

    /** Display samples are sent on change, not on every processing tick. */
    private void syncVisuals() {
        if (level != null && !level.isClientSide && (kind == DnaMachineKind.EXTRACTOR || kind == DnaMachineKind.SEQUENCER))
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("VisualSync", true);
        for (int slot = 0; slot < kind.inputs; slot++)
            if (visualSlot(slot)) tag.put("Visual" + slot, items.get(slot).save(new CompoundTag()));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return accepts(kind, slot, stack, hybridizerMode); }
    @Override public int[] getSlotsForFace(Direction side) {
        if (kind == DnaMachineKind.COMBINATOR)
            // Automation wrappers may cache the exposed slots. Keep the set stable across mode
            // changes; canPlaceItemThroughFace/canTakeItemThroughFace enforce the active mode.
            return side == Direction.DOWN ? new int[]{2, 11}
                    : new int[]{0, 1, 3, 4, 5, 6, 7, 8, 9, 10};
        return side == Direction.DOWN ? IntStream.range(kind.inputs, kind.slots).toArray() : IntStream.range(0, kind.inputs).toArray();
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack) && (kind != DnaMachineKind.SEQUENCER || items.get(slot).isEmpty());
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return kind == DnaMachineKind.COMBINATOR ? slot == (hybridizerMode ? 11 : 2) : slot >= kind.inputs;
    }
    public boolean hybridizerMode() { return kind == DnaMachineKind.COMBINATOR && hybridizerMode; }
    public void setHybridizerMode(boolean mode) {
        if (kind != DnaMachineKind.COMBINATOR || hybridizerMode == mode) return;
        hybridizerMode = mode;
        progress[0] = 0;
        setChanged();
    }
    @Override public Component getDisplayName() { return customName != null ? customName : Component.translatable("container.jurassicraft." + kind.id); }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override @Nullable public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new DnaMachineMenu(id, inventory, this, data, kind); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putIntArray("Progress", progress);
        if (kind == DnaMachineKind.COMBINATOR) tag.putBoolean("HybridizerMode", hybridizerMode);
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        // Client block-entity updates carry only the rendered inputs.
        if (tag.getBoolean("VisualSync")) {
            for (int slot = 0; slot < kind.inputs; slot++)
                if (visualSlot(slot)) items.set(slot, ItemStack.of(tag.getCompound("Visual" + slot)));
            return;
        }
        super.load(tag);
        items = NonNullList.withSize(kind.slots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        hybridizerMode = kind == DnaMachineKind.COMBINATOR && tag.getBoolean("HybridizerMode");
        int[] saved = tag.getIntArray("Progress");
        for (int i = 0; i < progress.length; i++) progress[i] = i < saved.length ? Math.max(0, Math.min(kind.ticks - 1, saved[i])) : 0;
        customName = tag.contains("CustomName", 8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }
}
