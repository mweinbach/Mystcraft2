package ports.gridwright.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** A generator or the resonator: an input slot, an output slot and a {@link MachineMenu} screen. */
public abstract class MachineBlockEntity extends InventoryBlockEntity implements MenuProvider {

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state, size);
    }

    public abstract MachineLayout layout();

    /** The values the screen shows, synchronised as 16-bit halves by {@link MachineMenu}. */
    public abstract ContainerData data();

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(containerId, inventory, this, data(), layout());
    }
}
