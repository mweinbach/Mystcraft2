package ports.buildcraft.lib;

import java.util.function.IntFunction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The server side of upstream's {@code SlotDisplay}: a read-only container whose stacks are computed, so vanilla
 * menu sync shows them in display slots on the client.
 */
public final class DisplayContainer implements Container {
    private final int size;
    private final IntFunction<ItemStack> display;

    public DisplayContainer(int size, IntFunction<ItemStack> display) {
        this.size = size;
        this.display = display;
    }

    @Override public int getContainerSize() { return size; }
    @Override public boolean isEmpty() { return false; }
    @Override public ItemStack getItem(int slot) { return display.apply(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) {}
    @Override public void setChanged() {}
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() {}
}
