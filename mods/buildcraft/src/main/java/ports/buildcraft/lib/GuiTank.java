package ports.buildcraft.lib;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.platform.ItemFluids;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code WidgetFluidTank} and {@code Tank.transferStackToTank}: a fluid container clicked on a
 * GUI tank first empties into the tank, and otherwise fills from it. Survival players trade one item
 * for its filled or emptied container; creative players keep what they hold.
 *
 * @param solids upstream's {@code map} override for items that turn into fluid, such as the
 *               combustion engine's ice; empty for every other item
 */
public record GuiTank(Tank tank, Function<ItemStack, FluidAmount> solids, Runnable changed) {
    public GuiTank(Tank tank, Runnable changed) {
        this(tank, stack -> FluidAmount.EMPTY, changed);
    }

    private record Mapped(ItemStack container, FluidAmount fluid) {}

    /** Returns what {@code stack} becomes; it may be changed in place. */
    public ItemStack transferStackToTank(Player player, ItemStack stack) {
        if (stack.isEmpty() || player.level.isClientSide) return stack;
        ItemStack original = stack;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        boolean creative = player.getAbilities().instabuild;

        Mapped mapped = map(copy, tank.space());
        if (mapped != null) {
            int accepted = tank.fill(mapped.fluid.fluid(), (int) mapped.fluid.amount(), true);
            if (creative ? accepted > 0 : accepted == mapped.fluid.amount()) {
                tank.fill(mapped.fluid.fluid(), accepted, false);
                changed.run();
                playSound(player, mapped.fluid.fluid(), false);
                if (creative) return original;
                stack.shrink(1);
                if (stack.isEmpty()) return mapped.container;
                if (!mapped.container.isEmpty()) player.getInventory().placeItemBackInInventory(mapped.container);
                return stack;
            }
        }

        ItemFluids item = Platform.get().itemFluids(copy);
        if (item == null) return stack;
        FluidAmount offered = tank.drain(null, tank.capacity(), true);
        if (offered.isEmpty()) return stack;
        long filled = item.fill(offered, false);
        if (filled <= 0) return stack;
        tank.drain(offered.fluid(), (int) filled, false);
        changed.run();
        playSound(player, offered.fluid(), true);
        if (creative) return stack;
        if (original.getCount() == 1) return item.container();
        if (!item.container().isEmpty()) player.getInventory().placeItemBackInInventory(item.container());
        original.shrink(1);
        return original;
    }

    @Nullable
    private Mapped map(ItemStack single, int space) {
        FluidAmount solid = solids.apply(single);
        if (!solid.isEmpty() && solid.amount() <= space) return new Mapped(ItemStack.EMPTY, solid);
        ItemFluids item = Platform.get().itemFluids(single);
        if (item == null) return null;
        FluidAmount drained = item.drain(space, false);
        return drained.isEmpty() ? null : new Mapped(item.container(), drained);
    }

    private static void playSound(Player player, Fluid fluid, boolean fill) {
        player.level.playSound(null, player.blockPosition(), Platform.get().bucketSound(fluid, fill), SoundSource.PLAYERS, 1, 1);
    }
}
