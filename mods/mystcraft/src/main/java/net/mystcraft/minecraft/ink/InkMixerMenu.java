package net.mystcraft.minecraft.ink;

import net.mystcraft.integration.registry.InkMixerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InkMixerMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;
    private final BlockPos pos;
    private final SimpleContainer result = new SimpleContainer(1);
    private final Inventory playerInventory;

    public InkMixerMenu(int id, Inventory player, FriendlyByteBuf buf) {
        this(id, player, new SimpleContainer(4), new SimpleContainerData(1 + InkAdditives.PROPERTIES.size()), buf.readBlockPos());
    }

    public InkMixerMenu(int id, Inventory player, Container inventory, ContainerData data, BlockPos pos) {
        super(InkMixerRegistry.INK_MIXER_MENU.get(), id);
        checkContainerSize(inventory, 4);
        checkContainerDataCount(data, 1 + InkAdditives.PROPERTIES.size());
        this.inventory = inventory;
        this.data = data;
        this.pos = pos;
        this.playerInventory = player;
        addSlot(new Slot(inventory, 0, 8, 27) {
            @Override public boolean mayPlace(ItemStack stack) { return net.mystcraft.minecraft.fluid.InkContainerAccess.isInkContainer(stack); }
        });
        addSlot(new Slot(inventory, 1, 8, 48) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.PAPER); }
        });
        addSlot(new Slot(inventory, 2, 152, 27) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(result, 0, 152, 48) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return (player.level.isClientSide || stillValid(player)) && !getItem().isEmpty(); }
            @Override public ItemStack remove(int amount) {
                InkMixerBlockEntity mixer = serverMixer();
                if (mixer == null) return player.player.level.isClientSide ? super.remove(amount) : ItemStack.EMPTY;
                if (!stillValid(player.player)) return ItemStack.EMPTY;
                ItemStack taken = mixer.takePanel(amount);
                refreshResult();
                return taken;
            }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(player, col + row * 9 + 9, 8 + col * 18, 99 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 8 + col * 18, 157));
        addDataSlots(data);
        refreshResult();
    }

    private InkMixerBlockEntity serverMixer() {
        if (playerInventory.player.level.isClientSide) return null;
        return playerInventory.player.level.getBlockEntity(pos) instanceof InkMixerBlockEntity mixer && mixer.inventory == inventory ? mixer : null;
    }
    private void refreshResult() {
        InkMixerBlockEntity mixer = serverMixer();
        if (mixer == null) return;
        ItemStack preview = mixer.preview();
        if (!ItemStack.matches(preview, result.getItem(0))) result.setItem(0, preview);
    }
    @Override public void broadcastChanges() { refreshResult(); super.broadcastChanges(); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!player.level.isClientSide && !stillValid(player)) return;
        refreshResult();
        super.clicked(slot, button, type, player);
        refreshResult();
    }

    public boolean hasInk() { return data.get(0) != 0; }
    public float probability(int index) { return index < 0 || index >= InkAdditives.PROPERTIES.size() ? 0 : data.get(index + 1) / 10000F; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if ((button != 0 && button != 1) || !stillValid(player) || getCarried().isEmpty()) return false;
        InkMixerBlockEntity mixer = (InkMixerBlockEntity) player.level.getBlockEntity(pos);
        int consumed = mixer.addItems(getCarried(), button == 1);
        if (getCarried().isEmpty()) setCarried(ItemStack.EMPTY);
        broadcastChanges();
        return consumed > 0;
    }

    @Override public boolean stillValid(Player player) {
        return player.level.getBlockEntity(pos) instanceof InkMixerBlockEntity mixer
                && mixer.inventory == inventory
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        if (!stillValid(player)) return ItemStack.EMPTY;
        refreshResult();
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 3) {
            int room = 0;
            for (int i = 4; i < 40; i++) {
                ItemStack existing = slots.get(i).getItem();
                if (existing.isEmpty()) room += stack.getMaxStackSize();
                else if (ItemStack.isSameItemSameTags(stack, existing)) room += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (room == 0) return ItemStack.EMPTY;
            ItemStack taken = slot.remove(Math.min(room, stack.getCount()));
            if (taken.isEmpty()) return ItemStack.EMPTY;
            moveItemStackTo(taken, 4, 40, true);
            if (!taken.isEmpty()) inventory.setItem(3, taken);
            slot.onTake(player, taken);
            refreshResult();
            return original;
        }
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, 40, true)) return ItemStack.EMPTY;
        } else if (net.mystcraft.minecraft.fluid.InkContainerAccess.isInkContainer(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.PAPER)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < 31) {
            if (!moveItemStackTo(stack, 31, 40, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 4, 31, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
