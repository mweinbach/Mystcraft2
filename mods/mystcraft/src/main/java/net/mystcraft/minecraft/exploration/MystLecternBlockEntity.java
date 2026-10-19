package net.mystcraft.minecraft.exploration;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.minecraft.item.LinkingBookItem;
import net.mystcraft.minecraft.writing.PageItem;

/** Source filtered, single-slot display inventory and map update broadcaster. */
public final class MystLecternBlockEntity extends BlockEntity implements net.mystcraft.minecraft.blockentity.BookHolder, Container {
    private ItemStack book = ItemStack.EMPTY;
    private short yaw, pitch;
    public MystLecternBlockEntity(BlockPos pos, BlockState state) { super(ExplorationRegistry.LECTERN_ENTITY.get(), pos, state); }
    public static boolean accepts(ItemStack stack) { return stack.getItem() instanceof LinkingBookItem || stack.getItem() instanceof PageItem || net.mystcraft.minecraft.writing.PageData.isLinkPanel(stack) || stack.is(Items.FILLED_MAP); }
    public ItemStack getBook() { return book; }
    public void setBook(ItemStack stack) {
        if (!stack.isEmpty() && !accepts(stack)) return;
        book = stack.copy(); if (!book.isEmpty()) book.setCount(1); changed();
    }
    private void changed() {
        setChanged();
        if (level != null) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); }
    }
    public ItemStack takeBook() { ItemStack result = book; book = ItemStack.EMPTY; changed(); return result; }
    public void setYaw(int value) { yaw = (short)((value - value % 90) % 360); changed(); }
    public void setPitch(int value) { pitch = (short)(value % 360); changed(); }
    public short getYaw() { return yaw; }
    public short getPitch() { return pitch; }
    public static void tick(net.minecraft.world.level.Level world, BlockPos pos, BlockState state, MystLecternBlockEntity lectern) {
        if (!(world instanceof ServerLevel level) || !lectern.book.is(Items.FILLED_MAP)) return;
        Integer id = MapItem.getMapId(lectern.book);
        var map = MapItem.getSavedData(lectern.book, level);
        if (id == null || map == null) return;
        for (var player : level.players()) {
            map.getHoldingPlayer(player);
            var packet = map.getUpdatePacket(id, player);
            if (packet != null) player.connection.send(packet);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("Book", book.save(new CompoundTag())); tag.putShort("Yaw", yaw); tag.putShort("Pitch", pitch); }
    @Override public void load(CompoundTag tag) { super.load(tag); book = ItemStack.of(tag.getCompound("Book")); if (!book.isEmpty()) book.setCount(1); yaw = (short)((tag.getInt("Yaw") - tag.getInt("Yaw") % 90) % 360); pitch = (short)(tag.getInt("Pitch") % 360); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return book.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? book : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int count) { return slot == 0 && count > 0 ? takeBook() : ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { if (slot != 0) return ItemStack.EMPTY; ItemStack result = book; book = ItemStack.EMPTY; return result; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot == 0) setBook(stack); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && accepts(stack); }
    @Override public void clearContent() { takeBook(); }
    @Override public boolean stillValid(Player player) { return !isRemoved() && level != null && player.level == level && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64; }
}
