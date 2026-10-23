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
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.transport.behaviour.WoodDiamondBehaviour;
import ports.buildcraft.transport.behaviour.WoodDiamondBehaviour.FilterMode;

/**
 * Upstream {@code ContainerDiamondWoodPipe}: nine phantom filter slots and the filter mode. Menu buttons
 * 0 to 2 select whitelist, blacklist and round robin ({@link FilterMode} ordinals).
 */
public class DiamondWoodPipeMenu extends BCMenu {
    @Nullable
    private final WoodDiamondBehaviour behaviour;
    private final ContainerData data;
    /** False for the fluid pipe, which upstream gives no round robin button. */
    public final boolean roundRobin;

    public DiamondWoodPipeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(WoodDiamondBehaviour.FILTER_COUNT), null,
                new SimpleContainerData(3), buffer.readBoolean());
    }

    public DiamondWoodPipeMenu(int id, Inventory inventory, BlockPos pos, WoodDiamondBehaviour behaviour) {
        this(id, inventory, pos, behaviour.filters, behaviour, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> behaviour.filterMode().ordinal();
                    case 1 -> behaviour.currentFilter();
                    default -> behaviour.filterValid() ? 1 : 0;
                };
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 3; }
        }, behaviour.allowsRoundRobin());
    }

    private DiamondWoodPipeMenu(int id, Inventory inventory, BlockPos pos, Container filters, @Nullable WoodDiamondBehaviour behaviour,
            ContainerData data, boolean roundRobin) {
        super(BCTransport.DIAMOND_WOOD_PIPE_MENU.get(), id, pos);
        this.behaviour = behaviour;
        this.data = data;
        this.roundRobin = roundRobin;
        addFullPlayerInventory(inventory, 79);
        for (int i = 0; i < WoodDiamondBehaviour.FILTER_COUNT; i++) {
            addSlot(new DiamondPipeMenu.PhantomSlot(filters, i, 8 + i * 18, 18));
        }
        addDataSlots(data);
    }

    public FilterMode filterMode() {
        return FilterMode.get(data.get(0));
    }

    public int currentFilter() {
        return data.get(1);
    }

    public boolean filterValid() {
        return data.get(2) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (behaviour == null || id < 0 || id >= FilterMode.values().length) return false;
        behaviour.setFilterMode(FilterMode.get(id));
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
