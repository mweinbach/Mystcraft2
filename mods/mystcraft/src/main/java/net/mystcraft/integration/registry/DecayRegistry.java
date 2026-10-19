package net.mystcraft.integration.registry;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.instability.DecayBlock;
import net.mystcraft.minecraft.instability.DecayType;

public final class DecayRegistry {
    private static final Map<DecayType, Supplier<Block>> BLOCKS = new EnumMap<>(DecayType.class);
    private static final Map<DecayType, Supplier<Item>> ITEMS = new EnumMap<>(DecayType.class);
    private DecayRegistry() {}
    public static Supplier<Block> block(DecayType type) { return BLOCKS.get(type); }
    public static Supplier<Item> item(DecayType type) { return ITEMS.get(type); }
    public static void registerBlocks(RegistryBridge bridge) {
        for (DecayType type : DecayType.values())
            BLOCKS.put(type, bridge.registerBlock("decay_" + type.id(), () -> new DecayBlock(type)));
    }
    public static void register(RegistryBridge bridge) {
        for (DecayType type : DecayType.values())
            ITEMS.put(type, bridge.registerItem("decay_" + type.id(), () -> new BlockItem(BLOCKS.get(type).get(), new Item.Properties().tab(MystcraftItems.TAB))));
    }
}
