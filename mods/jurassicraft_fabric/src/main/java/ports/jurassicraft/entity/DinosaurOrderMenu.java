package ports.jurassicraft.entity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Owner-validated menu; order changes use Minecraft's menu button packet. */
public final class DinosaurOrderMenu extends AbstractContainerMenu {
    private final DinosaurEntity dinosaur;

    public DinosaurOrderMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, inventory.player.level.getEntity(buffer.readVarInt()) instanceof DinosaurEntity animal ? animal : null);
    }

    public DinosaurOrderMenu(int id, Inventory inventory, DinosaurEntity dinosaur) {
        super(DinosaurRegistry.ORDER_MENU.get(), id);
        this.dinosaur = dinosaur;
    }

    public DinosaurEntity dinosaur() { return dinosaur; }

    @Override public boolean stillValid(Player player) {
        return dinosaur != null && dinosaur.isAlive() && dinosaur.level == player.level
                && dinosaur.getOwnerUUID() != null && dinosaur.getOwnerUUID().equals(player.getUUID())
                && dinosaur.distanceToSqr(player) <= 64;
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        DinosaurEntity.Order[] orders = DinosaurEntity.Order.values();
        if (id < 0 || id >= orders.length || !stillValid(player)) return false;
        if (!player.level.isClientSide) dinosaur.setOrder(orders[id]);
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
