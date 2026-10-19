package ports.jurassicraft.hatching;

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

public final class HatcheryMenu extends AbstractContainerMenu {
    // Vanilla serializes the button ID as a signed byte: 5 slots * 21 steps fit in 0..104.
    public static final int TEMPERATURE_STEPS = 21;
    private final HatcheryRegistry.Machine machine;
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final HatcheryBlockEntity entity;
    public HatcheryMenu(int id, Inventory inventory, FriendlyByteBuf buffer, HatcheryRegistry.Machine machine) {
        this(id,inventory,new SimpleContainer(machine.slots),new SimpleContainerData(machine == HatcheryRegistry.Machine.INCUBATOR ? 15 : 2),machine,
                ContainerLevelAccess.create(inventory.player.level,buffer.readBlockPos()),null);
    }
    public HatcheryMenu(int id, Inventory inventory, HatcheryBlockEntity entity, ContainerData data, HatcheryRegistry.Machine machine) {
        this(id,inventory,entity,data,machine,ContainerLevelAccess.create(inventory.player.level,entity.getBlockPos()),entity);
    }
    private HatcheryMenu(int id, Inventory inventory, Container container, ContainerData data, HatcheryRegistry.Machine machine, ContainerLevelAccess access, @Nullable HatcheryBlockEntity entity) {
        super(HatcheryRegistry.menu(machine),id);
        this.machine=machine; this.container=container; this.data=data; this.access=access; this.entity=entity;
        checkContainerSize(container,machine.slots); checkContainerDataCount(data,machine == HatcheryRegistry.Machine.INCUBATOR ? 15 : 2);
        container.startOpen(inventory.player);
        int[][] positions = switch (machine) {
            case EMBRYONIC -> new int[][]{{24,49},{50,49},{50,13},{119,26},{137,26},{119,44},{137,44}};
            case CALCIFIER -> new int[][]{{34,14},{34,50},{97,32}};
            case INCUBATOR -> new int[][]{{33,28},{56,21},{79,14},{102,21},{125,28},{79,49}};
        };
        for (int slot = 0; slot < positions.length; slot++) {
            final int index=slot;
            addSlot(new Slot(container,slot,positions[slot][0],positions[slot][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return HatcheryRecipes.accepts(machine,index,stack); }
            });
        }
        for (int row=0;row<3;row++) for (int column=0;column<9;column++) addSlot(new Slot(inventory,column+row*9+9,8+column*18,84+row*18));
        for (int column=0;column<9;column++) addSlot(new Slot(inventory,column,8+column*18,142));
        addDataSlots(data);
    }
    public HatcheryRegistry.Machine machine() { return machine; }
    public int progressScaled(int slot, int width) {
        int progress = machine == HatcheryRegistry.Machine.INCUBATOR ? data.get(slot) : data.get(0);
        int total = machine == HatcheryRegistry.Machine.INCUBATOR ? data.get(slot+5) : data.get(1);
        return total > 0 ? progress*width/total : 0;
    }
    public int temperature(int slot) { return data.get(slot+10); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (machine != HatcheryRegistry.Machine.INCUBATOR || entity == null || !stillValid(player)) return false;
        if (id < 0 || id >= 5 * TEMPERATURE_STEPS) return false;
        int slot = id / TEMPERATURE_STEPS;
        int value = (id % TEMPERATURE_STEPS) * 5;
        entity.setTemperature(slot,value); return true;
    }
    @Override public boolean stillValid(Player player) { return stillValid(access,player,HatcheryRegistry.block(machine)) && container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot=slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source=slot.getItem(), original=source.copy();
        int size=machine.slots, invEnd=size+27, end=size+36;
        if (index<size) { if (!moveItemStackTo(source,size,end,true)) return ItemStack.EMPTY; }
        else {
            boolean valid=false;
            for (int i=0;i<size;i++) if (HatcheryRecipes.accepts(machine,i,source)) { valid=true; break; }
            if (valid) { if (!moveItemStackTo(source,0,size,false)) return ItemStack.EMPTY; }
            else if (index<invEnd) { if (!moveItemStackTo(source,invEnd,end,false)) return ItemStack.EMPTY; }
            else if (!moveItemStackTo(source,size,invEnd,false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount()==original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,source); return original;
    }
}
