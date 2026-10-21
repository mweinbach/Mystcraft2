package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;

/** Upstream {@code InventoryUtil}: hand items to adjacent pipes, then inventories, then the ground. */
public final class InventoryUtil {
    /** Upstream {@code AutomaticProvidingTransactor}: pipes connect, but nothing can be inserted or pulled. */
    public static final ItemPort PROVIDING = new ItemPort() {
        @Override public ItemStack insert(ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extract(Predicate<ItemStack> filter, int max, boolean simulate) { return ItemStack.EMPTY; }
    };

    private InventoryUtil() {}

    public static ItemStack addToRandomInjectable(Level level, BlockPos pos, @Nullable Direction ignore, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (Direction face : shuffledDirections(level)) {
            if (face == ignore) continue;
            BlockEntity tile = level.getBlockEntity(pos.relative(face));
            if (tile instanceof Injectable injectable && injectable.canInjectItems(face.getOpposite())) {
                stack = injectable.injectItem(stack, true, face.getOpposite(), null, 0);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    public static ItemStack addToRandomInventory(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (Direction face : shuffledDirections(level)) {
            BlockPos target = pos.relative(face);
            if (level.getBlockEntity(target) instanceof Injectable) continue;
            ItemPort port = Platform.get().findItems(level, target, face.getOpposite());
            if (port == null) continue;
            stack = port.insert(stack, false);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    public static void addToBestAcceptor(Level level, BlockPos pos, @Nullable Direction ignore, ItemStack stack) {
        stack = addToRandomInjectable(level, pos, ignore, stack);
        stack = addToRandomInventory(level, pos, stack);
        drop(level, pos, stack);
    }

    public static void drop(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
    }

    private static List<Direction> shuffledDirections(Level level) {
        List<Direction> toTry = new ArrayList<>(6);
        Collections.addAll(toTry, Direction.values());
        Collections.shuffle(toTry, new Random(level.random.nextLong()));
        return toTry;
    }
}
