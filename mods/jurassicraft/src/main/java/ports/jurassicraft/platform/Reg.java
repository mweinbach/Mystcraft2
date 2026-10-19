package ports.jurassicraft.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;

/**
 * Typed shorthands for {@link Platform#register}. Suppliers resolve lazily on Forge and immediately
 * on Fabric, so a registry class must declare entries after the entries they depend on.
 */
public final class Reg {
    private Reg() {}

    public static ResourceLocation id(String name) { return new ResourceLocation(PortMod.MOD_ID, name); }

    public static <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        return Platform.register(Registry.BLOCK_REGISTRY, name, factory);
    }
    public static <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        return Platform.register(Registry.ITEM_REGISTRY, name, factory);
    }
    public static Supplier<Item> blockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
        return item(name, () -> new BlockItem(block.get(), properties));
    }
    public static <T extends BlockEntity> Supplier<BlockEntityType<T>> blockEntity(String name, Supplier<BlockEntityType<T>> factory) {
        return Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, name, factory);
    }
    public static <T extends Entity> Supplier<EntityType<T>> entity(String name, Supplier<EntityType<T>> factory) {
        return Platform.register(Registry.ENTITY_TYPE_REGISTRY, name, factory);
    }
    public static Supplier<SoundEvent> sound(String name) {
        return Platform.register(Registry.SOUND_EVENT_REGISTRY, name, () -> new SoundEvent(id(name)));
    }
}
