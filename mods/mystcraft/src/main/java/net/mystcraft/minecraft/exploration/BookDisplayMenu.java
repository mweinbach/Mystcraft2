package net.mystcraft.minecraft.exploration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.integration.registry.BookDisplayRegistry;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.mystcraft.minecraft.item.LinkingBookItem;

/** Native one-slot inventory corresponding to classic ContainerBook's display page. */
public final class BookDisplayMenu extends AbstractContainerMenu {
    public static final int READ = 0;
    private final Container display;
    private final BlockPos position;
    private final LinkBookEntity entity;
    public BookDisplayMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, new SimpleContainer(1), data.readBoolean() ? data.readBlockPos() : null, null);
    }
    public BookDisplayMenu(int id, Inventory inventory, Container display, BlockPos position, LinkBookEntity entity) {
        super(BookDisplayRegistry.DISPLAY_MENU.get(), id);
        this.display = display; this.position = position; this.entity = entity;
        addSlot(new Slot(display, 0, 80, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return display.canPlaceItem(0, stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
    }
    public ItemStack displayed() { return display.getItem(0); }
    @Override public boolean stillValid(Player player) { return player.isAlive() && !player.isSpectator() && display.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != READ || !(player instanceof ServerPlayer server) || !stillValid(player) || !(displayed().getItem() instanceof LinkingBookItem)) return false;
        if (position != null) LinkingNetwork.openLectern(server, position);
        else if (entity != null) LinkingNetwork.openEntity(server, entity);
        else return false;
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack current = slot.getItem(), before = current.copy();
        if (index == 0 ? !moveItemStackTo(current, 1, slots.size(), true) : !moveItemStackTo(current, 0, 1, false)) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }
}
