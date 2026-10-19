package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.modifier.LinkModifierBlock;
import net.mystcraft.minecraft.modifier.LinkModifierBlockEntity;
import net.mystcraft.minecraft.modifier.LinkModifierMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Creative/admin utility. Deliberately has no survival crafting recipe. */
public final class ModifierRegistry {
    public static Supplier<Block> LINK_MODIFIER;
    public static Supplier<Item> LINK_MODIFIER_ITEM;
    public static Supplier<BlockEntityType<LinkModifierBlockEntity>> LINK_MODIFIER_ENTITY;
    public static Supplier<MenuType<LinkModifierMenu>> LINK_MODIFIER_MENU;
    private ModifierRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) { LINK_MODIFIER = bridge.registerBlock("link_modifier", LinkModifierBlock::new); }
    public static void register(RegistryBridge bridge) {
        LINK_MODIFIER_ITEM = bridge.registerItem("link_modifier", () -> new BlockItem(LINK_MODIFIER.get(), new Item.Properties().tab(MystcraftItems.TAB)));
        LINK_MODIFIER_ENTITY = bridge.registerBlockEntityType("link_modifier", () -> BlockEntityType.Builder.of(LinkModifierBlockEntity::new, LINK_MODIFIER.get()).build(null));
        LINK_MODIFIER_MENU = bridge.registerMenu("link_modifier", LinkModifierMenu::new);
    }
}
