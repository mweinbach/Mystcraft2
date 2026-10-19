package ports.jurassicraft.hatching;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HatcheryBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedScreenHandlerFactory {
    private final HatcheryRegistry.Machine machine;
    private NonNullList<ItemStack> items;
    private final ItemStack[] visualEggs = {ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    private final int[] progress = new int[5];
    private final int[] temperature = new int[5];
    private final ItemStack[] active = {ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    @Nullable private Component customName;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            if (machine != HatcheryRegistry.Machine.INCUBATOR) return i == 0 ? progress[0] : i == 1 ? machine.ticks : 0;
            if (i < 5) return progress[i];
            if (i < 10) return machine.ticks;
            if (i < 15) return temperature[i - 10];
            return 0;
        }
        @Override public void set(int i, int value) {
            if (machine != HatcheryRegistry.Machine.INCUBATOR) { if (i == 0) progress[0] = value; }
            else if (i < 5) progress[i] = value; else if (i >= 10 && i < 15) temperature[i - 10] = value;
        }
        @Override public int getCount() { return machine == HatcheryRegistry.Machine.INCUBATOR ? 15 : 2; }
    };
    public HatcheryBlockEntity(BlockPos pos, BlockState state, HatcheryRegistry.Machine machine) {
        super(HatcheryRegistry.blockEntity(machine), pos, state);
        this.machine = machine;
        this.items = NonNullList.withSize(machine.slots, ItemStack.EMPTY);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, HatcheryBlockEntity entity) {
        if (level.isClientSide) return;
        if (entity.machine == HatcheryRegistry.Machine.INCUBATOR) entity.tickIncubator();
        else entity.tickMachine();
    }
    private void tickMachine() {
        boolean changedInput = false;
        for (int i = 0; i < machine.inputs; i++) {
            ItemStack current = items.get(i);
            if (!same(current, active[i])) { active[i] = one(current); changedInput = true; }
        }
        if (changedInput) progress[0] = 0;
        ItemStack result = machine == HatcheryRegistry.Machine.EMBRYONIC
                ? HatcheryRecipes.embryonic(items.get(0), items.get(1), items.get(2))
                : HatcheryRecipes.calcify(items.get(0), items.get(1));
        int output = outputSlot(result);
        if (!result.isEmpty() && output >= 0) {
            if (++progress[0] >= machine.ticks) {
                if (items.get(output).isEmpty()) items.set(output,result);
                else items.get(output).grow(result.getCount());
                for (int i = 0; i < machine.inputs; i++) removeItem(i,1);
                progress[0] = 0;
            }
            setChanged();
        } else if (progress[0] != 0 || changedInput) { progress[0] = 0; setChanged(); }
    }
    private int outputSlot(ItemStack result) {
        if (result.isEmpty()) return -1;
        for (int i = machine.inputs; i < machine.slots; i++) {
            ItemStack existing = items.get(i);
            if (existing.isEmpty() || (ItemStack.isSameItemSameTags(existing,result) && existing.getCount() + result.getCount() <= existing.getMaxStackSize())) return i;
        }
        return -1;
    }
    private void tickIncubator() {
        boolean dirty = false, visualChanged = false;
        for (int slot = 0; slot < 5; slot++) {
            ItemStack egg = items.get(slot);
            if (!same(egg, active[slot])) { progress[slot] = 0; active[slot] = one(egg); dirty = true; }
            ItemStack result = HatcheryRecipes.incubate(egg,temperature[slot]);
            if (!result.isEmpty() && HatcheryRecipes.isEnvironment(items.get(5))) {
                progress[slot]++;
                dirty = true;
                if (progress[slot] >= machine.ticks) {
                    items.set(slot, result);
                    visualChanged = true;
                    removeItem(5,1);
                    active[slot] = one(result);
                    progress[slot] = 0;
                }
            } else if (progress[slot] > 0) { progress[slot]--; dirty = true; }
        }
        if (dirty) setChanged();
        if (visualChanged) syncVisuals();
    }
    private static boolean same(ItemStack a, ItemStack b) { return a.isEmpty() && b.isEmpty() || (!a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a,b)); }
    private static ItemStack one(ItemStack stack) { if (stack.isEmpty()) return ItemStack.EMPTY; ItemStack copy = stack.copy(); copy.setCount(1); return copy; }
    public void setTemperature(int slot, int value) { if (slot >= 0 && slot < 5) { temperature[slot] = Math.max(0, Math.min(100,value)); setChanged(); } }
    @Override public int getContainerSize() { return machine.slots; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed=ContainerHelper.removeItem(items,slot,amount);
        if (!removed.isEmpty()) { setChanged(); if (slot < 5) syncVisuals(); }
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed=ContainerHelper.takeItem(items,slot);
        if (!removed.isEmpty() && slot < 5) syncVisuals();
        return removed;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot,stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged(); if (slot < 5) syncVisuals();
    }
    @Override public void clearContent() { items.clear(); setChanged(); syncVisuals(); }
    public ItemStack visualEgg(int slot) { return visualEggs[slot]; }
    private void syncVisuals() {
        if (machine == HatcheryRegistry.Machine.INCUBATOR && level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("VisualSync", true);
        if (machine == HatcheryRegistry.Machine.INCUBATOR)
            for (int slot = 0; slot < 5; slot++) tag.put("Visual" + slot, items.get(slot).save(new CompoundTag()));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5) <= 64; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return HatcheryRecipes.accepts(machine,slot,stack); }
    @Override public int[] getSlotsForFace(Direction side) {
        if (machine == HatcheryRegistry.Machine.INCUBATOR) return new int[]{0,1,2,3,4,5};
        return side == Direction.DOWN ? java.util.stream.IntStream.range(machine.inputs,machine.slots).toArray() : java.util.stream.IntStream.range(0,machine.inputs).toArray();
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (machine != HatcheryRegistry.Machine.INCUBATOR) return slot >= machine.inputs;
        if (slot >= 5) return false;
        for (var species : ports.jurassicraft.content.Species.values()) if (HatcheryRegistry.hatchedEgg(species) != null && stack.is(HatcheryRegistry.hatchedEgg(species))) return true;
        return false;
    }
    @Override public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buffer) { buffer.writeBlockPos(worldPosition); }
    @Override public Component getDisplayName() { return customName != null ? customName : Component.translatable("container.jurassicraft." + machine.id); }
    public void setCustomName(Component name) { customName=name; setChanged(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new HatcheryMenu(id,inventory,this,data,machine); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag,items);
        tag.putIntArray("Progress",progress); tag.putIntArray("Temperature",temperature);
        for (int i = 0; i < active.length; i++) if (!active[i].isEmpty())
            tag.put("Active" + i, active[i].save(new CompoundTag()));
        if (customName != null) tag.putString("CustomName",Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        if (tag.getBoolean("VisualSync")) {
            if (machine == HatcheryRegistry.Machine.INCUBATOR)
                for (int slot = 0; slot < 5; slot++) visualEggs[slot] = ItemStack.of(tag.getCompound("Visual" + slot));
            return;
        }
        super.load(tag); items=NonNullList.withSize(machine.slots,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,items);
        int[] p=tag.getIntArray("Progress"), t=tag.getIntArray("Temperature");
        for (int i=0;i<5;i++) {
            progress[i] = i < p.length ? Math.max(0,Math.min(machine.ticks-1,p[i])) : 0;
            temperature[i] = i < t.length ? Math.max(0,Math.min(100,t[i])) : 0;
            active[i] = tag.contains("Active" + i, 10) ? ItemStack.of(tag.getCompound("Active" + i)) : ItemStack.EMPTY;
        }
        customName=tag.contains("CustomName",8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }
}
