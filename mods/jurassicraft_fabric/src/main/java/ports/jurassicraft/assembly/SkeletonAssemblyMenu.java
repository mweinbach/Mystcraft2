package ports.jurassicraft.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SkeletonAssemblyMenu extends AbstractContainerMenu {
    private final CraftingContainer grid = new CraftingContainer(this, 5, 5);
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;

    public SkeletonAssemblyMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos()); }
    public SkeletonAssemblyMenu(int id, Inventory inventory, BlockPos pos) {
        super(AssemblyRegistry.MENU.get(), id);
        player = inventory.player;
        access = ContainerLevelAccess.create(player.level, pos);
        addSlot(new Slot(result, 0, 140, 52) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public void onTake(Player player, ItemStack stack) {
                // Each displayed fossil position consumes precisely one bone.
                for (int i = 0; i < 25; i++) if (!grid.getItem(i).isEmpty()) grid.removeItem(i, 1);
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            addSlot(new Slot(grid, col + row * 5, 16 + col * 18, 16 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return SkeletonRecipes.isBone(stack); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 119 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 177));
    }
    @Override public void slotsChanged(Container inventory) {
        if (!player.level.isClientSide) {
            SkeletonRecipes.Match match = SkeletonRecipes.find(grid);
            result.setItem(0, match == null ? ItemStack.EMPTY : SkeletonDisplayItem.create(match.species(), match.fresh()));
            broadcastChanges();
        }
    }
    @Override public boolean stillValid(Player player) { return stillValid(access, player, AssemblyRegistry.TABLE.get()); }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level.isClientSide) clearContainer(player, grid);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0 || index < 26) {
            if (!moveItemStackTo(stack, 26, 62, true)) return ItemStack.EMPTY;
        } else if (index < 53) {
            if (!moveItemStackTo(stack, 53, 62, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 26, 53, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return original;
    }
}
