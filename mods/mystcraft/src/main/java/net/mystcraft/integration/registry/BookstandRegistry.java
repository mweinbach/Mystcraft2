package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.mystcraft.integration.platform.RegistryBridge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.mystcraft.minecraft.block.BookstandBlock;
import net.mystcraft.minecraft.blockentity.BookstandBlockEntity;

public final class BookstandRegistry {
    public static Supplier<Block> BOOKSTAND;
    public static Supplier<BlockEntityType<BookstandBlockEntity>> BOOKSTAND_ENTITY;
    private BookstandRegistry() {}

    public static void registerBlocks(RegistryBridge bridge) {
        BOOKSTAND = bridge.registerBlock("bookstand", () -> new BookstandBlock(
            BlockBehaviour.Properties.of(Material.WOOD).strength(2).sound(SoundType.WOOD).noOcclusion()));
    }
    public static void register(RegistryBridge bridge) {
        BOOKSTAND_ENTITY = bridge.registerBlockEntityType(
            "bookstand", () -> BlockEntityType.Builder.of(BookstandBlockEntity::new, BOOKSTAND.get()).build(null));
    }
}
