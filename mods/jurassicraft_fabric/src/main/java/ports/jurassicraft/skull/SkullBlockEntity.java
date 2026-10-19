package ports.jurassicraft.skull;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keep the actual skull, including its DNA and pose tag, through saves and removal. */
public final class SkullBlockEntity extends BlockEntity {
    private ItemStack skull=ItemStack.EMPTY;
    private float angle;
    private boolean suppressDrops;
    public SkullBlockEntity(BlockPos pos,BlockState state) { super(SkullRegistry.ENTITY.get(),pos,state); }
    public void setSkull(ItemStack input,float angle) {
        skull=input.copy(); skull.setCount(1); this.angle=angle; setChanged();
        if (level!=null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    public ItemStack skull() { return skull.copy(); }
    public boolean fresh() { return SkullPlacement.isFresh(skull); }
    public boolean stand() { return SkullPlacement.hasStand(skull); }
    public float angle() { return angle; }
    public void suppressDrops() { suppressDrops=true; }
    public boolean shouldDrop() { return !suppressDrops && !skull.isEmpty(); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); if (!skull.isEmpty()) tag.put("Skull",skull.save(new CompoundTag())); tag.putFloat("Angle",angle);
    }
    @Override public void load(CompoundTag tag) { super.load(tag); skull=tag.contains("Skull",10) ? ItemStack.of(tag.getCompound("Skull")) : ItemStack.EMPTY; angle=tag.getFloat("Angle"); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
