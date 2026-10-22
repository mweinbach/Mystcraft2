package ports.jurassicraft.cleaning;

import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.fossil.FossilLootEntry;

public final class CleaningStationBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    /** Client setup replaces this. The dedicated server keeps the empty spawner. */
    public static Consumer<CleaningStationBlockEntity> clientWash = station -> {};
    public static final int INPUT = 0;
    public static final int FUEL = 1;
    public static final int FIRST_OUTPUT = 2;
    public static final int SLOT_COUNT = 8;
    public static final int WATER_TICKS = 1600;
    public static final int CLEAN_TICKS = 200;

    private int water;
    private int progress;
    private boolean visualActive;
    private ItemStack visualInput = ItemStack.EMPTY;
    private float visualRotation, previousRotation, rotationSpeed;
    private ItemStack pendingInput = ItemStack.EMPTY;
    private ItemStack pendingResult = ItemStack.EMPTY;
    @Nullable private Component customName;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private static final int[] ACCESSIBLE = {0, 1, 2, 3, 4, 5, 6, 7};

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) { case 0 -> water; case 1 -> WATER_TICKS; case 2 -> progress; case 3 -> CLEAN_TICKS; default -> 0; };
        }
        @Override public void set(int index, int value) {
            if (index == 0) water = value;
            if (index == 2) progress = value;
        }
        @Override public int getCount() { return 4; }
    };

    public CleaningStationBlockEntity(BlockPos pos, BlockState state) {
        super(CleaningStationRegistry.BLOCK_ENTITY.get(), pos, state);
    }

    private static Cleanable customCleanable(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof Cleanable item) return item;
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof Cleanable block) return block;
        return null;
    }

    public static boolean isCleanable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Cleanable custom = customCleanable(stack);
        if (custom != null) return custom.isCleanable(stack);
        if (stack.is(JurassicContent.PLANT_FOSSIL_BLOCK.get().asItem())) return true;
        for (Species species : Species.values()) {
            if (stack.is(JurassicContent.ENCASED_FOSSILS.get(species).get().asItem())) return true;
        }
        return false;
    }

    /** Matches the upstream result roll, including the plant fossil's non-fossil byproducts. */
    static ItemStack chooseResult(ItemStack input, RandomSource random) {
        Cleanable custom = customCleanable(input);
        if (custom != null) return custom.isCleanable(input) ? custom.clean(input, random) : ItemStack.EMPTY;
        if (input.is(JurassicContent.PLANT_FOSSIL_BLOCK.get().asItem())) {
            return new ItemStack(FossilLootEntry.choosePlant(random, 2));
        }
        for (Species species : Species.values()) {
            if (input.is(JurassicContent.ENCASED_FOSSILS.get(species).get().asItem())) {
                String bone = species.bones().get(random.nextInt(species.bones().size()));
                return new ItemStack(JurassicContent.BONES.get(species).get(bone).get());
            }
        }
        return ItemStack.EMPTY;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CleaningStationBlockEntity station) {
        station.tick(level.random);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CleaningStationBlockEntity station) {
        station.previousRotation = station.visualRotation;
        station.rotationSpeed = station.visualActive
                ? Math.min(1.2F, (station.rotationSpeed + .008F) * 1.01F)
                : Math.max(0, (station.rotationSpeed - .01F) * .95F);
        station.visualRotation += station.rotationSpeed;
        clientWash.accept(station);
    }

    public ItemStack visualInput() { return visualInput; }
    public boolean visualActive() { return visualActive; }
    public float visualRotation(float partialTick) { return Mth.lerp(partialTick, previousRotation, visualRotation); }
    private void setVisualActive(boolean active) {
        if (visualActive != active) { visualActive = active; syncVisuals(); }
    }
    private void syncVisuals() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("VisualInput", getItem(INPUT).save(new CompoundTag()));
        tag.putBoolean("VisualActive", visualActive);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    private void tick(RandomSource random) {
        ItemStack input = getItem(INPUT);
        if (!ItemStack.isSameItemSameTags(input, pendingInput)) {
            // A selected output belongs to a specific input. Never carry it over to a different fossil.
            pendingInput = input.copy();
            pendingInput.setCount(input.isEmpty() ? 0 : 1);
            pendingResult = ItemStack.EMPTY;
            progress = 0;
            setChanged();
        }
        if (!isCleanable(input)) { setVisualActive(false); return; }
        if (pendingResult.isEmpty()) {
            pendingResult = chooseResult(input, random);
            setChanged();
        }
        int output = findOutputSlot(pendingResult);
        if (output < 0) { setVisualActive(false); return; } // No water or progress is lost while blocked.

        if (water == 0 && getItem(FUEL).is(Items.WATER_BUCKET)) {
            setItem(FUEL, new ItemStack(Items.BUCKET));
            water = WATER_TICKS;
        }
        if (water <= 0) { setVisualActive(false); return; }

        setVisualActive(true);
        --water;
        if (++progress >= CLEAN_TICKS) {
            ItemStack present = getItem(output);
            if (present.isEmpty()) setItem(output, pendingResult.copy());
            else {
                ItemStack combined = present.copy();
                combined.grow(pendingResult.getCount());
                setItem(output, combined);
            }
            removeItem(INPUT, 1);
            progress = 0;
            pendingResult = ItemStack.EMPTY;
        }
        setChanged();
    }

    private int findOutputSlot(ItemStack result) {
        if (result.isEmpty()) return -1;
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            ItemStack present = getItem(slot);
            int capacity = Math.min(result.getMaxStackSize(), getMaxStackSize());
            if ((present.isEmpty() && result.getCount() <= capacity)
                    || (!present.isEmpty() && ItemStack.isSameItemSameTags(present, result)
                    && present.getCount() + result.getCount() <= capacity)) return slot;
        }
        return -1;
    }

    ContainerData data() { return data; }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) { setChanged(); if (slot == INPUT) syncVisuals(); }
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty() && slot == INPUT) syncVisuals();
        return removed;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
        if (slot == INPUT) syncVisuals();
    }
    @Override public void clearContent() { items.clear(); setChanged(); syncVisuals(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT ? isCleanable(stack) : slot == FUEL && stack.is(Items.WATER_BUCKET);
    }
    @Override public int[] getSlotsForFace(Direction side) { return ACCESSIBLE; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= FIRST_OUTPUT || (slot == FUEL && stack.is(Items.BUCKET));
    }
    public void dropContents(Level level, BlockPos pos) { Containers.dropContents(level, pos, this); }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.jurassicraft.cleaning_station");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CleaningStationMenu(id, inventory, this);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", SLOT_COUNT);
        ContainerHelper.saveAllItems(inventory, items);
        tag.put("Inventory", inventory);
        tag.putInt("Water", water);
        tag.putInt("Progress", progress);
        if (!pendingInput.isEmpty()) tag.put("PendingInput", pendingInput.save(new CompoundTag()));
        if (!pendingResult.isEmpty()) tag.put("PendingResult", pendingResult.save(new CompoundTag()));
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        // Update packets carry only animation state; do not clear the client inventory.
        if (tag.contains("VisualInput", 10)) {
            visualInput = ItemStack.of(tag.getCompound("VisualInput"));
            visualActive = tag.getBoolean("VisualActive");
            return;
        }
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound("Inventory"), items);
        water = Mth.clamp(tag.getInt("Water"), 0, WATER_TICKS);
        progress = Mth.clamp(tag.getInt("Progress"), 0, CLEAN_TICKS - 1);
        pendingInput = ItemStack.of(tag.getCompound("PendingInput"));
        pendingResult = ItemStack.of(tag.getCompound("PendingResult"));
        customName = tag.contains("CustomName", 8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }

}
