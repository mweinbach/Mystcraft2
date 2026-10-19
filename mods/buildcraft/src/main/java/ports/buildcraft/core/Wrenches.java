package ports.buildcraft.core;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Wrench detection: BuildCraft's own wrench plus the conventional wrench tags of both loaders. */
public final class Wrenches {
    public static final TagKey<Item> COMMON_WRENCHES = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("c", "wrenches"));
    public static final TagKey<Item> FORGE_WRENCHES = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "tools/wrench"));
    /** Upstream {@code VanillaRotationHandlers.ROTATE_FACING}. */
    public static final Direction[] ROTATE_FACING = {Direction.EAST, Direction.SOUTH, Direction.DOWN, Direction.WEST, Direction.NORTH, Direction.UP};

    private Wrenches() {}

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof WrenchItem || stack.is(COMMON_WRENCHES) || stack.is(FORGE_WRENCHES));
    }

    /** The facing after {@code current} in upstream's rotation order; null starts at the beginning. */
    public static Direction nextFacing(Direction current) {
        for (int i = 0; i < ROTATE_FACING.length; i++) {
            if (ROTATE_FACING[i] == current) return ROTATE_FACING[(i + 1) % ROTATE_FACING.length];
        }
        return ROTATE_FACING[0];
    }
}
