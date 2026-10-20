package ports.buildcraft.lib;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Upstream {@code ContainerBC_Neptune}: player inventory first, then machine slots. GUI tanks are
 * clicked through vanilla's menu button packet, whose id is the tank's index.
 */
public abstract class BCMenu extends AbstractContainerMenu {
    protected final BlockPos pos;
    protected int machineSlotStart = 36;
    /** Shift-clicked stacks go into machine slots before this index; later slots are display or output only. */
    protected int machineSlotEnd = Integer.MAX_VALUE;
    private List<GuiTank> guiTanks = List.of();
    private List<GuiTank> quickMoveTanks = List.of();

    protected BCMenu(@Nullable MenuType<?> type, int id, BlockPos pos) {
        super(type, id);
        this.pos = pos;
    }

    protected void addFullPlayerInventory(Inventory inventory, int startY) {
        addFullPlayerInventory(inventory, 8, startY);
    }

    protected void addFullPlayerInventory(Inventory inventory, int startX, int startY) {
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot(new Slot(inventory, sx + sy * 9 + 9, startX + sx * 18, startY + sy * 18));
            }
        }
        for (int sx = 0; sx < 9; sx++) {
            addSlot(new Slot(inventory, sx, startX + sx * 18, startY + 58));
        }
    }

    /**
     * Server side only: the tanks a carried container can be clicked on, by button id, and those a
     * shift-clicked inventory container tries in order, as upstream's {@code transferStackInSlot}.
     */
    protected void setGuiTanks(List<GuiTank> clickable, List<GuiTank> quickMove) {
        guiTanks = List.copyOf(clickable);
        quickMoveTanks = List.copyOf(quickMove);
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
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= guiTanks.size()) return false;
        setCarried(guiTanks.get(id).transferStackToTank(player, getCarried()));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlotStart) {
            for (GuiTank tank : quickMoveTanks) {
                ItemStack result = tank.transferStackToTank(player, stack);
                if (!ItemStack.matches(result, original)) {
                    slot.set(result);
                    return ItemStack.EMPTY;
                }
            }
            int end = Math.min(machineSlotEnd, slots.size());
            if (machineSlotStart >= end || !moveItemStackTo(stack, machineSlotStart, end, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlotStart, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
