package ports.buildcraft.transport;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour.SlotIndex;

/**
 * Upstream {@code ContainerEmzuliPipe_BC8}: the four phantom filter slots and their paint colours. Menu button
 * {@code slot * 3 + key} changes a slot's paint as upstream's paint buttons do: key 0 steps to the next colour
 * (after black, no paint), key 1 to the previous one and key 2 clears it.
 */
public class EmzuliPipeMenu extends BCMenu {
    public static final int[][] FILTER_POSITIONS = {{25, 21}, {25, 49}, {134, 21}, {134, 49}};
    private static final int DATA_ACTIVE = 4, DATA_CURRENT = 5;

    @Nullable
    private final EmzuliBehaviour behaviour;
    private final ContainerData data;

    public EmzuliPipeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(SlotIndex.VALUES.length), null, new SimpleContainerData(6));
    }

    public EmzuliPipeMenu(int id, Inventory inventory, BlockPos pos, EmzuliBehaviour behaviour) {
        this(id, inventory, pos, behaviour.filters, behaviour, new ContainerData() {
            @Override
            public int get(int index) {
                if (index < SlotIndex.VALUES.length) {
                    DyeColor colour = behaviour.slotColour(SlotIndex.VALUES[index]);
                    return colour == null ? 0 : colour.getId() + 1;
                }
                if (index == DATA_ACTIVE) {
                    int active = 0;
                    for (SlotIndex slot : behaviour.activeSlots()) active |= 1 << slot.ordinal();
                    return active;
                }
                SlotIndex current = behaviour.currentSlot();
                return current == null ? 0 : current.ordinal() + 1;
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 6; }
        });
    }

    private EmzuliPipeMenu(int id, Inventory inventory, BlockPos pos, Container filters, @Nullable EmzuliBehaviour behaviour, ContainerData data) {
        super(BCTransport.EMZULI_PIPE_MENU.get(), id, pos);
        this.behaviour = behaviour;
        this.data = data;
        addFullPlayerInventory(inventory, 84);
        for (int i = 0; i < FILTER_POSITIONS.length; i++) {
            addSlot(new DiamondPipeMenu.PhantomSlot(filters, i, FILTER_POSITIONS[i][0], FILTER_POSITIONS[i][1]));
        }
        addDataSlots(data);
    }

    @Nullable
    public DyeColor slotColour(SlotIndex index) {
        int value = data.get(index.ordinal());
        return value <= 0 || value > 16 ? null : DyeColor.byId(value - 1);
    }

    public boolean isActive(SlotIndex index) {
        return (data.get(DATA_ACTIVE) & (1 << index.ordinal())) != 0;
    }

    @Nullable
    public SlotIndex currentSlot() {
        int value = data.get(DATA_CURRENT);
        return value <= 0 || value > SlotIndex.VALUES.length ? null : SlotIndex.VALUES[value - 1];
    }

    /** Upstream {@code ColourUtil.getNextOrNull}. */
    @Nullable
    public static DyeColor nextOrNull(@Nullable DyeColor colour) {
        if (colour == null) return DyeColor.byId(0);
        return colour.getId() == 15 ? null : DyeColor.byId(colour.getId() + 1);
    }

    /** Upstream {@code ColourUtil.getPrevOrNull}. */
    @Nullable
    public static DyeColor prevOrNull(@Nullable DyeColor colour) {
        if (colour == null) return DyeColor.byId(15);
        return colour.getId() == 0 ? null : DyeColor.byId(colour.getId() - 1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (behaviour == null || id < 0 || id >= SlotIndex.VALUES.length * 3) return false;
        SlotIndex index = SlotIndex.VALUES[id / 3];
        DyeColor old = behaviour.slotColour(index);
        DyeColor colour = switch (id % 3) {
            case 0 -> nextOrNull(old);
            case 1 -> prevOrNull(old);
            default -> null;
        };
        behaviour.setSlotColour(index, colour);
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (DiamondPipeMenu.clickPhantom(this, slotId)) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
