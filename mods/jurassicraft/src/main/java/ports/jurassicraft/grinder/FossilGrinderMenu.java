package ports.jurassicraft.grinder;

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

public final class FossilGrinderMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client-side mirror; the server synchronizes its slots and progress. */
    public FossilGrinderMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(FossilGrinderBlockEntity.SLOT_COUNT), new SimpleContainerData(2),
                ContainerLevelAccess.create(inventory.player.level, buffer.readBlockPos()));
    }
    public FossilGrinderMenu(int id, Inventory inventory, FossilGrinderBlockEntity grinder, ContainerData data) {
        this(id, inventory, grinder, data, ContainerLevelAccess.create(inventory.player.level, grinder.getBlockPos()));
    }
    private FossilGrinderMenu(int id, Inventory inventory, Container container, ContainerData data, ContainerLevelAccess access) {
        super(FossilGrinderRegistry.FOSSIL_GRINDER_MENU.get(), id);
        checkContainerSize(container, 12);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;
        this.access = access;
        container.startOpen(inventory.player);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 2; column++) {
                addSlot(new Slot(container, row + column * 3, row * 18 + 23, column * 18 + 26) {
                    @Override public boolean mayPlace(ItemStack stack) { return FossilGrinderRecipes.accepts(stack); }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 2; column++) {
                addSlot(new Slot(container, row + column * 3 + 6, row * 18 + 108, column * 18 + 26) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    public int progressScaled(int width) {
        int total = data.get(1);
        return total > 0 ? data.get(0) * width / total : 0;
    }
    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, FossilGrinderRegistry.FOSSIL_GRINDER.get()) && container.stillValid(player);
    }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (index < 12) {
            if (!moveItemStackTo(source, 12, 48, true)) return ItemStack.EMPTY;
        } else if (FossilGrinderRecipes.accepts(source)) {
            if (!moveItemStackTo(source, 0, 6, false)) return ItemStack.EMPTY;
        } else if (index < 39) {
            if (!moveItemStackTo(source, 39, 48, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 12, 39, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return original;
    }
}
