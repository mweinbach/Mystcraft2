package ports.jurassicraft.cultivator;

import javax.annotation.Nullable;
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

public final class CultivatorMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final CultivatorBlockEntity entity;
    public CultivatorMenu(int id,Inventory inv,FriendlyByteBuf buffer) {
        this(id,inv,new SimpleContainer(4),new SimpleContainerData(8),ContainerLevelAccess.create(inv.player.level,buffer.readBlockPos()),null);
    }
    public CultivatorMenu(int id,Inventory inv,CultivatorBlockEntity entity,ContainerData data) {
        this(id,inv,entity,data,ContainerLevelAccess.create(inv.player.level,entity.getBlockPos()),entity);
    }
    private CultivatorMenu(int id,Inventory inv,Container container,ContainerData data,ContainerLevelAccess access,@Nullable CultivatorBlockEntity entity) {
        super(CultivatorRegistry.MENU.get(),id); this.container=container; this.data=data; this.access=access; this.entity=entity;
        checkContainerSize(container,4); checkContainerDataCount(data,8); container.startOpen(inv.player);
        int[][] pos={{122,44},{208,20},{12,20},{12,68}};
        for (int i=0;i<4;i++) { final int slot=i; addSlot(new Slot(container,i,pos[i][0],pos[i][1]) {
            @Override public boolean mayPlace(ItemStack stack) { return CultivatorBlockEntity.accepts(slot,stack); }
        }); }
        for (int row=0;row<3;row++) for (int col=0;col<9;col++) addSlot(new Slot(inv,col+row*9+9,8+col*18,106+row*18));
        for (int col=0;col<9;col++) addSlot(new Slot(inv,col,8+col*18,164));
        addDataSlots(data);
    }
    public int field(int index) { return data.get(index); }
    @Override public boolean clickMenuButton(Player player,int id) {
        if (id<0 || id>20 || entity==null || !stillValid(player)) return false;
        entity.setTemperature(id*5); return true;
    }
    @Override public boolean stillValid(Player player) { return stillValid(access,player,CultivatorRegistry.CULTIVATOR.get()) && container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        Slot slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),copy=source.copy();
        if (index<4) { if (!moveItemStackTo(source,4,40,true)) return ItemStack.EMPTY; }
        else {
            boolean valid=false; for (int i=0;i<3;i++) if (CultivatorBlockEntity.accepts(i,source)) valid=true;
            if (valid) { if (!moveItemStackTo(source,0,3,false)) return ItemStack.EMPTY; }
            else if (index<31) { if (!moveItemStackTo(source,31,40,false)) return ItemStack.EMPTY; }
            else if (!moveItemStackTo(source,4,31,false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (copy.getCount()==source.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,source); return copy;
    }
}
