package net.mystcraft.minecraft.modifier;

import java.util.List;
import net.mystcraft.integration.registry.ModifierRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.ink.InkAdditives;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class LinkModifierMenu extends AbstractContainerMenu {
    public static final List<String> FLAGS = List.copyOf(InkAdditives.PROPERTIES);
    public static final int TITLE_CLEAR = 1, TITLE_APPLY = 2, SEED_CLEAR = 3, SEED_APPLY = 4;
    public static final int FLAG_START = 100, TITLE_CHAR = 0x10000, SEED_CHAR = 0x20000;
    private final LinkModifierBlockEntity modifier;
    private final Container inventory;
    private final BlockPos pos;
    private String titleBuffer = "", seedBuffer = "";
    public LinkModifierMenu(int id, Inventory player, FriendlyByteBuf buf) { this(id, player, null, new SimpleContainer(1), buf.readBlockPos()); }
    public LinkModifierMenu(int id, Inventory player, LinkModifierBlockEntity modifier) { this(id, player, modifier, modifier.inventory, modifier.getBlockPos()); }
    private LinkModifierMenu(int id, Inventory player, LinkModifierBlockEntity modifier, Container inventory, BlockPos pos) {
        super(ModifierRegistry.LINK_MODIFIER_MENU.get(), id);
        this.modifier = modifier; this.inventory = inventory; this.pos = pos;
        addSlot(new Slot(inventory, 0, 80, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return LinkModifierBlockEntity.isBook(stack); }
            @Override public boolean mayPickup(Player player) { return player.level.isClientSide || stillValid(player); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(player, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 8 + col * 18, 142));
    }
    public ItemStack book() { return inventory.getItem(0); }
    public String title() { return book().hasCustomHoverName() ? book().getHoverName().getString() : ""; }
    public String seed() { return book().hasTag() ? book().getTag().getCompound("Props").getString("Seed") : ""; }
    public boolean seedEditable() { return AgeBookData.isUnallocated(book()); }
    public boolean flag(int index) {
        LinkModifiers flags = LinkModifiers.from(book());
        return switch(index) {
            case 0 -> flags.intraLinking(); case 1 -> flags.intraLinkingOnly(); case 2 -> flags.generatePlatform();
            case 3 -> flags.maintainMomentum(); case 4 -> flags.disarm(); case 5 -> flags.relative(); default -> false;
        };
    }
    @Override public boolean stillValid(Player player) {
        return LinkModifierBlockEntity.authorized(player) && player.level.getBlockEntity(pos) == modifier && modifier != null
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!player.level.isClientSide && !stillValid(player)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public boolean clickMenuButton(Player player, int command) {
        if (!stillValid(player) || !LinkModifierBlockEntity.isBook(book())) return false;
        boolean accepted = true;
        if (command == TITLE_CLEAR) titleBuffer = "";
        else if (command == TITLE_APPLY) accepted = modifier.rename(player, titleBuffer);
        else if (command == SEED_CLEAR) seedBuffer = "";
        else if (command == SEED_APPLY) accepted = modifier.setSeed(player, seedBuffer);
        else if (command >= FLAG_START && command < FLAG_START + FLAGS.size()) {
            int index = command - FLAG_START;
            accepted = modifier.setFlag(player, FLAGS.get(index), !flag(index));
        } else if ((command & 0xffff0000) == TITLE_CHAR && titleBuffer.length() < 21 && !Character.isISOControl((char)command)) titleBuffer += (char) command;
        else if ((command & 0xffff0000) == SEED_CHAR && seedBuffer.length() < 21 && (Character.isDigit((char)command) || (char)command == '-' || (char)command == '+')) seedBuffer += (char) command;
        else accepted = false;
        broadcastChanges();
        return accepted;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index == 0) { if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY; }
        else if (LinkModifierBlockEntity.isBook(stack)) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (index < 28) { if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
