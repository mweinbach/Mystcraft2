package ports.jurassicraft.bugs;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BugCrateMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    public BugCrateMenu(int id,Inventory inventory,FriendlyByteBuf buffer) {
        this(id,inventory,new SimpleContainer(9),new SimpleContainerData(2),ContainerLevelAccess.create(inventory.player.level,buffer.readBlockPos()));
    }
    public BugCrateMenu(int id,Inventory inventory,BugCrateBlockEntity entity,ContainerData data) {
        this(id,inventory,entity,data,ContainerLevelAccess.create(inventory.player.level,entity.getBlockPos()));
    }
    private BugCrateMenu(int id,Inventory inventory,Container container,ContainerData data,ContainerLevelAccess access) {
        super(BugRegistry.MENU.get(),id); this.container=container; this.data=data; this.access=access;
        checkContainerSize(container,9); checkContainerDataCount(data,2); container.startOpen(inventory.player);
        for (int slot=0;slot<9;slot++) { final int index=slot; int x=slot<6 ? 26+(slot%3)*18 : 126; int y=slot<3 ? 17 : slot<6 ? 51 : 17+(slot-6)*18;
            addSlot(new Slot(container,slot,x,y) { @Override public boolean mayPlace(ItemStack stack) { return index<3 ? BugFoods.isBug(stack) : index<6 && BugFoods.isFood(stack); } });
        }
        for (int row=0;row<3;row++) for (int column=0;column<9;column++) addSlot(new Slot(inventory,column+row*9+9,8+column*18,84+row*18));
        for (int column=0;column<9;column++) addSlot(new Slot(inventory,column,8+column*18,142));
        addDataSlots(data);
    }
    public int progress(int scale) { return data.get(1)>0 ? data.get(0)*scale/data.get(1) : 0; }
    @Override public boolean stillValid(Player player) { return stillValid(access,player,BugRegistry.CRATE.get()) && container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        Slot slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),original=source.copy();
        if (index<9) { if (!moveItemStackTo(source,9,45,true)) return ItemStack.EMPTY; }
        else if (BugFoods.isBug(source)) { if (!moveItemStackTo(source,0,3,false)) return ItemStack.EMPTY; }
        else if (BugFoods.isFood(source)) { if (!moveItemStackTo(source,3,6,false)) return ItemStack.EMPTY; }
        else if (index<36) { if (!moveItemStackTo(source,36,45,false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(source,9,36,false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount()==original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,source); return original;
    }
}
