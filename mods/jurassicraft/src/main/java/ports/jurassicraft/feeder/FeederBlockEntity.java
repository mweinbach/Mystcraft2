package ports.jurassicraft.feeder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import ports.jurassicraft.entity.DinosaurEntity;

public final class FeederBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MEAT_SLOTS = 9;
    public static final int SLOT_COUNT = 18;
    @Nullable private Component customName;
    private boolean visualOpen;
    private long closeAt;
    private float previousLid, lid;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot < MEAT_SLOTS ? FeederFood.meat(stack) : FeederFood.plant(stack);
        }
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    };
    // Like the original feeder, automation fills but does not remove its contents.
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return inventory.getSlots(); }
        @Nonnull @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Nonnull @Override public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) { return inventory.insertItem(slot, stack, simulate); }
        @Nonnull @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) { return inventory.isItemValid(slot, stack); }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automation);

    public FeederBlockEntity(BlockPos pos, BlockState state) { super(FeederRegistry.BLOCK_ENTITY.get(), pos, state); }

    ItemStackHandler inventory() { return inventory; }

    private int foodSlot(DinosaurEntity dinosaur) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && dinosaur.isFood(stack)) return slot;
        }
        return -1;
    }

    /** Used by dinosaurs looking for food without changing the inventory. */
    public boolean hasFoodFor(DinosaurEntity dinosaur) { return foodSlot(dinosaur) >= 0; }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FeederBlockEntity feeder) {
        feeder.previousLid = feeder.lid;
        feeder.lid = Mth.clamp(feeder.lid + (feeder.visualOpen ? .05F : -.05F), 0, 1);
    }
    public float lidOpening(float partialTick) { return Mth.lerp(partialTick, previousLid, lid); }
    private void setVisualOpen(boolean open) {
        if (visualOpen == open) return;
        visualOpen = open;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag(); tag.putBoolean("VisualOpen", visualOpen); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) { visualOpen = tag.getBoolean("VisualOpen"); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FeederBlockEntity feeder) {
        if (feeder.visualOpen && level.getGameTime() >= feeder.closeAt) feeder.setVisualOpen(false);
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) != 0) return;
        for (DinosaurEntity dinosaur : level.getEntitiesOfClass(DinosaurEntity.class, new AABB(pos).inflate(2.0D))) {
            if (!dinosaur.isAlive() || !dinosaur.needsFood()) continue;
            int slot = feeder.foodSlot(dinosaur);
            if (slot < 0) continue;
            ItemStack serving = feeder.inventory.getStackInSlot(slot).copy();
            serving.setCount(1);
            if (dinosaur.feed(serving)) {
                feeder.inventory.extractItem(slot, 1, false);
                feeder.closeAt = level.getGameTime() + 40;
                feeder.setVisualOpen(true);
                break; // Serve at most one dinosaur per second.
            }
        }
    }

    public int comparatorSignal() {
        float fullness = 0;
        int occupied = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                fullness += (float) stack.getCount() / Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
                occupied++;
            }
        }
        return (int) (fullness / SLOT_COUNT * 14.0F) + (occupied > 0 ? 1 : 0);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override public Component getDisplayName() { return customName != null ? customName : Component.translatable("container.jurassicraft.feeder"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return new FeederMenu(id, playerInventory, this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        customName = tag.contains("CustomName", 8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }
    @Nonnull @Override public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCapability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCapability = LazyOptional.of(() -> automation); }
}
