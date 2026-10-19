package ports.buildcraft.lib;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Upstream {@code ContainerBC_Neptune}: player inventory first, then machine slots. */
public abstract class BCMenu extends AbstractContainerMenu {
    protected final BlockPos pos;
    protected int machineSlotStart = 36;

    protected BCMenu(@Nullable MenuType<?> type, int id, BlockPos pos) {
        super(type, id);
        this.pos = pos;
    }

    protected void addFullPlayerInventory(Inventory inventory, int startY) {
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot(new Slot(inventory, sx + sy * 9 + 9, 8 + sx * 18, startY + sy * 18));
            }
        }
        for (int sx = 0; sx < 9; sx++) {
            addSlot(new Slot(inventory, sx, 8 + sx * 18, startY + 58));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && !player.level.getBlockState(pos).isAir();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlotStart) {
            if (machineSlotStart >= slots.size() || !moveItemStackTo(stack, machineSlotStart, slots.size(), false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlotStart, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
