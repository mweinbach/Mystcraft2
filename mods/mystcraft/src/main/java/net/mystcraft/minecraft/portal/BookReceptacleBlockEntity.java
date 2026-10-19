package net.mystcraft.minecraft.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.PortalRegistry;

public final class BookReceptacleBlockEntity extends BlockEntity {
    private ItemStack book = ItemStack.EMPTY;
    private short yaw;
    private short pitch;
    public BookReceptacleBlockEntity(BlockPos pos, BlockState state) { super(PortalRegistry.RECEPTACLE_ENTITY.get(), pos, state); }
    public ItemStack getBook() { return book; }
    public short getYaw() { return yaw; }
    public short getPitch() { return pitch; }
    public void setYaw(int value) { yaw = (short) (value % 360); presentationChanged(); }
    public void setPitch(int value) { pitch = (short) (value % 360); presentationChanged(); }
    private void presentationChanged() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public void setBook(ItemStack stack) {
        if (level != null && !level.isClientSide) PortalController.shutdown(level, worldPosition);
        book = stack.copy();
        if (!book.isEmpty()) book.setCount(1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            if (!level.isClientSide && !book.isEmpty()) PortalController.activate(level, worldPosition);
        }
    }
    public ItemStack takeBook() { ItemStack result = book; setBook(ItemStack.EMPTY); return result; }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("Book", book.save(new CompoundTag())); tag.putShort("Yaw", yaw); tag.putShort("Pitch", pitch); }
    @Override public void load(CompoundTag tag) { super.load(tag); book = ItemStack.of(tag.getCompound("Book")); if (!book.isEmpty()) book.setCount(1); yaw = (short) (tag.getInt("Yaw") % 360); pitch = (short) (tag.getInt("Pitch") % 360); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
