package ports.jurassicraft.feeder;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class FeederMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final Container feeder;
    public FeederMenu(int id,Inventory playerInventory) { this(id,playerInventory,new SimpleContainer(FeederBlockEntity.SLOT_COUNT),ContainerLevelAccess.NULL); }
    public FeederMenu(int id,Inventory playerInventory,FeederBlockEntity feeder) { this(id,playerInventory,feeder,ContainerLevelAccess.create(playerInventory.player.level,feeder.getBlockPos())); }
    private FeederMenu(int id,Inventory playerInventory,Container feeder,ContainerLevelAccess access) {
        super(FeederRegistry.MENU.get(),id); this.access=access; this.feeder=feeder;
        checkContainerSize(feeder,FeederBlockEntity.SLOT_COUNT); feeder.startOpen(playerInventory.player);
        for (int row=0;row<3;row++) for (int col=0;col<3;col++) addFeederSlot(feeder,row*3+col,26+col*18,18+row*18);
        for (int row=0;row<3;row++) for (int col=0;col<3;col++) addFeederSlot(feeder,9+row*3+col,98+col*18,18+row*18);
        for (int row=0;row<3;row++) for (int col=0;col<9;col++) addSlot(new Slot(playerInventory,col+row*9+9,8+col*18,84+row*18));
        for (int col=0;col<9;col++) addSlot(new Slot(playerInventory,col,8+col*18,142));
    }
    private void addFeederSlot(Container feeder,int slot,int x,int y) {
        addSlot(new Slot(feeder,slot,x,y) { @Override public boolean mayPlace(ItemStack stack) { return slot<FeederBlockEntity.MEAT_SLOTS ? FeederFood.meat(stack) : FeederFood.plant(stack); } });
    }
    @Override public boolean stillValid(Player player) { return stillValid(access,player,FeederRegistry.BLOCK.get()) && feeder.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); feeder.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if (index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if (index<FeederBlockEntity.SLOT_COUNT) { if (!moveItemStackTo(stack,18,54,true)) return ItemStack.EMPTY; }
        else {
            boolean moved=FeederFood.meat(stack) ? moveItemStackTo(stack,0,9,false) : FeederFood.plant(stack) && moveItemStackTo(stack,9,18,false);
            if (!moved) moved=index<45 ? moveItemStackTo(stack,45,54,false) : moveItemStackTo(stack,18,45,false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount()==original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,stack); return original;
    }
}
