package ports.jurassicraft.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class DnaMachineMenu extends AbstractContainerMenu {
    private final DnaMachineKind kind;
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private boolean clickMode;

    public DnaMachineMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer.readEnum(DnaMachineKind.class));
    }
    private DnaMachineMenu(int id, Inventory inventory, net.minecraft.core.BlockPos pos, DnaMachineKind kind) {
        this(id, inventory, new SimpleContainer(kind.slots), new SimpleContainerData(5), kind,
                ContainerLevelAccess.create(inventory.player.level, pos));
    }
    public DnaMachineMenu(int id, Inventory inventory, DnaMachineBlockEntity machine, ContainerData data, DnaMachineKind kind) {
        this(id, inventory, machine, data, kind, ContainerLevelAccess.create(inventory.player.level, machine.getBlockPos()));
    }
    private DnaMachineMenu(int id, Inventory inventory, Container container, ContainerData data, DnaMachineKind kind, ContainerLevelAccess access) {
        super(DnaMachineRegistry.MENU.get(), id);
        checkContainerSize(container, kind.slots);
        checkContainerDataCount(data, 5);
        this.kind = kind; this.container = container; this.data = data; this.access = access;
        this.clickMode = hybridizerMode();
        container.startOpen(inventory.player);
        int[][] positions = positions(kind);
        for (int i = 0; i < kind.slots; i++) {
            final int slotIndex = i;
            addSlot(new Slot(container, i, positions[i][0], positions[i][1]) {
                @Override public boolean isActive() { return isSlotActive(slotIndex); }
                @Override public boolean mayPlace(ItemStack stack) { return isActive() && DnaMachineBlockEntity.accepts(kind, slotIndex, stack, hybridizerMode()); }
                @Override public boolean mayPickup(Player player) { return isActive(); }
                @Override public int getMaxStackSize() { return kind == DnaMachineKind.SEQUENCER ? 1 : super.getMaxStackSize(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    public static int[][] positions(DnaMachineKind kind) {
        return switch (kind) {
            case EXTRACTOR -> new int[][] {{55,26},{55,47},{108,28},{126,28},{108,46},{126,46}};
            case SEQUENCER -> new int[][] {{44,16},{66,16},{44,36},{66,36},{44,56},{66,56},{113,16},{113,36},{113,56}};
            case COMBINATOR -> new int[][] {{55,13},{105,13},{81,60},
                    {10,17},{30,17},{50,17},{70,17},{90,17},{110,17},{130,17},{150,17},{80,56}};
            case SYNTHESIZER -> new int[][] {{38,22},{24,49},{50,49},{119,26},{137,26},{119,44},{137,44}};
        };
    }
    public DnaMachineKind kind() { return kind; }
    public int progressScaled(int lane, int width) { int total = data.get(3); return total > 0 ? data.get(lane) * width / total : 0; }
    public boolean hybridizerMode() { return kind == DnaMachineKind.COMBINATOR && data.get(4) != 0; }
    public boolean isSlotActive(int slot) { return kind != DnaMachineKind.COMBINATOR || (hybridizerMode() ? slot >= 3 : slot <= 2); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (clickMode != hybridizerMode()) {
            // Another player may have changed modes during this menu's drag operation.
            resetQuickCraft();
            clickMode = hybridizerMode();
        }
        // Vanilla invokes some item-specific and creative click handlers before mayPickup.
        // Do not let a stale or forged packet interact with the other mode's inventory.
        if (slot >= 0 && slot < kind.slots && !isSlotActive(slot)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || kind != DnaMachineKind.COMBINATOR || player.level.isClientSide
                || !(container instanceof DnaMachineBlockEntity machine) || !stillValid(player)) return false;
        machine.setHybridizerMode(!machine.hybridizerMode());
        resetQuickCraft();
        clickMode = hybridizerMode();
        return true;
    }
    public String slotRole(int slot) {
        if (kind == DnaMachineKind.COMBINATOR) return slot == 2 || slot == 11 ? "output" : hybridizerMode() ? "hybrid_disc" : "filled_disc";
        if (slot >= kind.inputs) return "output";
        return switch (kind) {
            case EXTRACTOR -> slot == 0 ? "extract_sample" : "blank_disc";
            case SEQUENCER -> slot % 2 == 0 ? "tissue" : "blank_disc";
            case COMBINATOR -> "filled_disc";
            case SYNTHESIZER -> slot == 0 ? "complete_disc" : slot == 1 ? "test_tube" : "nucleotides";
        };
    }
    private Block expectedBlock() {
        return switch (kind) {
            case EXTRACTOR -> DnaMachineRegistry.EXTRACTOR.get();
            case SEQUENCER -> DnaMachineRegistry.SEQUENCER.get();
            case COMBINATOR -> DnaMachineRegistry.COMBINATOR.get();
            case SYNTHESIZER -> DnaMachineRegistry.SYNTHESIZER.get();
        };
    }
    @Override public boolean stillValid(Player player) { return stillValid(access, player, expectedBlock()) && container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.isActive() || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < kind.slots) {
            if (!moveItemStackTo(stack, kind.slots, kind.slots + 36, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < kind.slots && !stack.isEmpty(); i++) {
                if (slots.get(i).mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) moved = true;
            }
            if (!moved) {
                if (index < kind.slots + 27) {
                    if (!moveItemStackTo(stack, kind.slots + 27, kind.slots + 36, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, kind.slots, kind.slots + 27, false)) return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
