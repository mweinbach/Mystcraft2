package ports.jurassicraft.cleaning;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import ports.jurassicraft.fossil.FossilLootEntry;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

public final class CleaningStationBlockEntity extends BlockEntity implements MenuProvider {
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

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == INPUT) return isCleanable(stack);
            if (slot == FUEL) return stack.is(Items.WATER_BUCKET);
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == INPUT) syncVisuals();
        }
    };

    // Automation may insert inputs/fuel, and retrieve outputs and returned buckets.
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return inventory.getSlots(); }
        @Nonnull @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }
        @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= FIRST_OUTPUT || (slot == FUEL && inventory.getStackInSlot(slot).is(Items.BUCKET))) {
                return inventory.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) { return inventory.isItemValid(slot, stack); }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automation);

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
    }

    public ItemStack visualInput() { return visualInput; }
    public float visualRotation(float partialTick) { return Mth.lerp(partialTick, previousRotation, visualRotation); }
    private void setVisualActive(boolean active) {
        if (visualActive != active) { visualActive = active; syncVisuals(); }
    }
    private void syncVisuals() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("VisualInput", inventory.getStackInSlot(INPUT).save(new CompoundTag()));
        tag.putBoolean("VisualActive", visualActive);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        visualInput = ItemStack.of(tag.getCompound("VisualInput"));
        visualActive = tag.getBoolean("VisualActive");
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    private void tick(RandomSource random) {
        ItemStack input = inventory.getStackInSlot(INPUT);
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

        if (water == 0 && inventory.getStackInSlot(FUEL).is(Items.WATER_BUCKET)) {
            inventory.setStackInSlot(FUEL, new ItemStack(Items.BUCKET));
            water = WATER_TICKS;
        }
        if (water <= 0) { setVisualActive(false); return; }

        setVisualActive(true);
        --water;
        if (++progress >= CLEAN_TICKS) {
            ItemStack present = inventory.getStackInSlot(output);
            if (present.isEmpty()) inventory.setStackInSlot(output, pendingResult.copy());
            else {
                ItemStack combined = present.copy();
                combined.grow(pendingResult.getCount());
                inventory.setStackInSlot(output, combined);
            }
            inventory.extractItem(INPUT, 1, false);
            progress = 0;
            pendingResult = ItemStack.EMPTY;
        }
        setChanged();
    }

    private int findOutputSlot(ItemStack result) {
        if (result.isEmpty()) return -1;
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            ItemStack present = inventory.getStackInSlot(slot);
            int capacity = Math.min(result.getMaxStackSize(), inventory.getSlotLimit(slot));
            if ((present.isEmpty() && result.getCount() <= capacity)
                    || (!present.isEmpty() && ItemStack.isSameItemSameTags(present, result)
                    && present.getCount() + result.getCount() <= capacity)) return slot;
        }
        return -1;
    }

    ItemStackHandler inventory() { return inventory; }
    ContainerData data() { return data; }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.jurassicraft.cleaning_station");
    }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CleaningStationMenu(id, inventory, this);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Water", water);
        tag.putInt("Progress", progress);
        if (!pendingInput.isEmpty()) tag.put("PendingInput", pendingInput.save(new CompoundTag()));
        if (!pendingResult.isEmpty()) tag.put("PendingResult", pendingResult.save(new CompoundTag()));
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        water = Mth.clamp(tag.getInt("Water"), 0, WATER_TICKS);
        progress = Mth.clamp(tag.getInt("Progress"), 0, CLEAN_TICKS - 1);
        pendingInput = ItemStack.of(tag.getCompound("PendingInput"));
        pendingResult = ItemStack.of(tag.getCompound("PendingResult"));
        customName = tag.contains("CustomName", 8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }

    @Nonnull @Override public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCapability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCapability = LazyOptional.of(() -> automation); }
}
