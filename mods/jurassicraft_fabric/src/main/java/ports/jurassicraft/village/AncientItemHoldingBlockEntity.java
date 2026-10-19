package ports.jurassicraft.village;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AncientItemHoldingBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    private int rotation;
    private float xOffset, yOffset, zOffset;
    public AncientItemHoldingBlockEntity(BlockPos pos, BlockState state) { super(VillageRegistry.ITEM_HOLDER_ENTITY.get(), pos, state); }
    public ItemStack item() { return item; }
    public int rotation() { return rotation; }
    public float xOffset() { return xOffset; }
    public float yOffset() { return yOffset; }
    public float zOffset() { return zOffset; }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        item = ItemStack.of(tag.getCompound("item"));
        rotation = tag.getInt("itemRotation");
        xOffset = tag.getFloat("itemXOffset"); yOffset = tag.getFloat("itemYOffset"); zOffset = tag.getFloat("itemZOffset");
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!item.isEmpty()) tag.put("item", item.save(new CompoundTag()));
        tag.putInt("itemRotation", rotation);
        tag.putFloat("itemXOffset", xOffset); tag.putFloat("itemYOffset", yOffset); tag.putFloat("itemZOffset", zOffset);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override @Nullable public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
