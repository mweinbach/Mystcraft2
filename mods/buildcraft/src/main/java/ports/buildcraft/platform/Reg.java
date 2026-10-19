package ports.buildcraft.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

/**
 * Typed shorthands for {@link Platform#register}. Suppliers resolve lazily on Forge and once the
 * Fabric backend flushes its queue, so entries must only be dereferenced after registration.
 */
public final class Reg {
    private Reg() {}

    public static <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        return Platform.register(Registry.BLOCK_REGISTRY, name, factory);
    }

    public static <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        return Platform.register(Registry.ITEM_REGISTRY, name, factory);
    }

    public static <T extends Fluid> Supplier<T> fluid(String name, Supplier<T> factory) {
        return Platform.register(Registry.FLUID_REGISTRY, name, factory);
    }

    public static Supplier<Item> blockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
        return item(name, () -> new BlockItem(block.get(), properties));
    }

    public static <T extends BlockEntity> Supplier<BlockEntityType<T>> blockEntity(String name, Supplier<BlockEntityType<T>> factory) {
        return Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, name, factory);
    }
}
