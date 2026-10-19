package ports.jurassicraft.feeder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.entity.DinosaurEntity;

public final class FeederBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int MEAT_SLOTS=9, SLOT_COUNT=18;
    private static final int[] SLOTS={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17};
    private NonNullList<ItemStack> items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY);
    @Nullable private Component customName;
    private boolean visualOpen;
    private long closeAt;
    private float previousLid,lid;

    public FeederBlockEntity(BlockPos pos,BlockState state) { super(FeederRegistry.BLOCK_ENTITY.get(),pos,state); }
    private int foodSlot(DinosaurEntity dinosaur) {
        for (int slot=0;slot<SLOT_COUNT;slot++) if (!items.get(slot).isEmpty() && dinosaur.isFood(items.get(slot))) return slot;
        return -1;
    }
    public boolean hasFoodFor(DinosaurEntity dinosaur) { return foodSlot(dinosaur)>=0; }
    public static void clientTick(Level level,BlockPos pos,BlockState state,FeederBlockEntity feeder) {
        feeder.previousLid=feeder.lid;
        feeder.lid=Mth.clamp(feeder.lid+(feeder.visualOpen ? .05F : -.05F),0,1);
    }
    public float lidOpening(float partialTick) { return Mth.lerp(partialTick,previousLid,lid); }
    private void setVisualOpen(boolean open) {
        if (visualOpen==open) return;
        visualOpen=open;
        if (level!=null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    @Override public CompoundTag getUpdateTag() { CompoundTag tag=new CompoundTag(); tag.putBoolean("VisualOpen",visualOpen); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public static void serverTick(Level level,BlockPos pos,BlockState state,FeederBlockEntity feeder) {
        if (feeder.visualOpen && level.getGameTime()>=feeder.closeAt) feeder.setVisualOpen(false);
        if (Math.floorMod(level.getGameTime()+pos.asLong(),20)!=0) return;
        for (DinosaurEntity dinosaur : level.getEntitiesOfClass(DinosaurEntity.class,new AABB(pos).inflate(2.0D))) {
            if (!dinosaur.isAlive() || !dinosaur.needsFood()) continue;
            int slot=feeder.foodSlot(dinosaur);
            if (slot<0) continue;
            ItemStack serving=feeder.items.get(slot).copy(); serving.setCount(1);
            if (dinosaur.feed(serving)) {
                feeder.removeItem(slot,1); feeder.closeAt=level.getGameTime()+40; feeder.setVisualOpen(true); break;
            }
        }
    }
    public int comparatorSignal() {
        float fullness=0; int occupied=0;
        for (ItemStack stack : items) if (!stack.isEmpty()) {
            fullness+=(float)stack.getCount()/Math.min(getMaxStackSize(),stack.getMaxStackSize()); occupied++;
        }
        return (int)(fullness/SLOT_COUNT*14.0F)+(occupied>0 ? 1 : 0);
    }
    public void dropContents(Level level,BlockPos pos) { Containers.dropContents(level,pos,this); }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot,int count) { ItemStack result=ContainerHelper.removeItem(items,slot,count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack result=ContainerHelper.takeItem(items,slot); if (!result.isEmpty()) setChanged(); return result; }
    @Override public void setItem(int slot,ItemStack stack) { items.set(slot,stack); if (stack.getCount()>getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) { return level!=null && level.getBlockEntity(worldPosition)==this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64; }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return slot>=0 && slot<SLOT_COUNT && (slot<MEAT_SLOTS ? FeederFood.meat(stack) : FeederFood.plant(stack)); }
    @Override public int[] getSlotsForFace(Direction direction) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction side) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side) { return false; }
    @Override public void setChanged() {
        super.setChanged();
        if (level!=null && !level.isClientSide) level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
    }
    public void setCustomName(Component name) { customName=name; setChanged(); }
    @Override public Component getDisplayName() { return customName!=null ? customName : Component.translatable("container.jurassicraft.feeder"); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player) { return new FeederMenu(id,inventory,this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag inventory=new CompoundTag(); ContainerHelper.saveAllItems(inventory,items); inventory.putInt("Size",SLOT_COUNT); tag.put("Inventory",inventory);
        if (customName!=null) tag.putString("CustomName",Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory",10)) {
            items=NonNullList.withSize(SLOT_COUNT,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag.getCompound("Inventory"),items);
            customName=tag.contains("CustomName",8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
        }
        if (tag.contains("VisualOpen")) visualOpen=tag.getBoolean("VisualOpen");
    }
}
