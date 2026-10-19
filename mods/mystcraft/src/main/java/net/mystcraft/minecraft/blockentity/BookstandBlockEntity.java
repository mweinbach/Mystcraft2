package net.mystcraft.minecraft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.BookstandRegistry;

/** One persistent book; inventory is deliberately inaccessible to automation. */
public final class BookstandBlockEntity extends BlockEntity implements BookHolder {
    private ItemStack book = ItemStack.EMPTY;
    public BookstandBlockEntity(BlockPos pos, BlockState state) {
        super(BookstandRegistry.BOOKSTAND_ENTITY.get(), pos, state);
    }
    public ItemStack getBook() { return book; }
    public void setBook(ItemStack stack) {
        book = stack.copy();
        if (!book.isEmpty()) book.setCount(1);
        changed();
    }
    public ItemStack takeBook() {
        ItemStack result = book;
        book = ItemStack.EMPTY;
        changed();
        return result;
    }
    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!book.isEmpty()) tag.put("Book", book.save(new CompoundTag()));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        book = tag.contains("Book", 10) ? ItemStack.of(tag.getCompound("Book")) : ItemStack.EMPTY;
        if (!book.isEmpty()) book.setCount(1);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
