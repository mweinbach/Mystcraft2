package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.mystcraft.integration.platform.RegistryBridge;

import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.ink.InkMixerBlock;
import net.mystcraft.minecraft.ink.InkMixerBlockEntity;
import net.mystcraft.minecraft.ink.InkVialRecipe;
import net.mystcraft.minecraft.ink.InkMixerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class InkMixerRegistry {
    public static Supplier<Block> INK_MIXER;
    public static Supplier<Item> INK_MIXER_ITEM;
    public static Supplier<Item> INK_VIAL;
    public static Supplier<BlockEntityType<InkMixerBlockEntity>> INK_MIXER_ENTITY;
    public static Supplier<RecipeSerializer<InkVialRecipe>> INK_VIAL_RECIPE;
    public static Supplier<MenuType<InkMixerMenu>> INK_MIXER_MENU;



    private InkMixerRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) {
        INK_MIXER = bridge.registerBlock("ink_mixer", InkMixerBlock::new);
    }
    public static void register(RegistryBridge bridge) {
        INK_MIXER_ITEM = bridge.registerItem("ink_mixer", () -> new BlockItem(INK_MIXER.get(), new Item.Properties().tab(MystcraftItems.TAB)));
        INK_VIAL = bridge.registerItem("ink_vial", () -> new Item(new Item.Properties().tab(MystcraftItems.TAB).stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));
        INK_MIXER_ENTITY = bridge.registerBlockEntityType("ink_mixer", () -> BlockEntityType.Builder.of(InkMixerBlockEntity::new, INK_MIXER.get()).build(null));
        INK_VIAL_RECIPE = bridge.registerRecipeSerializer("ink_vial", () -> new SimpleRecipeSerializer<>(InkVialRecipe::new));
        INK_MIXER_MENU = bridge.registerMenu("ink_mixer", InkMixerMenu::new);
    }
}
