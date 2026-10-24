package com.agricraft.agricraft.common.registry;

import net.minecraft.core.Registry;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.block.CropStickVariant;
import com.agricraft.agricraft.common.item.ChannelValveItem;
import com.agricraft.agricraft.common.item.ClipperItem;
import com.agricraft.agricraft.common.item.TooltipBlockItem;
import com.agricraft.agricraft.common.item.CropSticksItem;
import com.agricraft.agricraft.common.item.DebuggerItem;
import com.agricraft.agricraft.common.item.JournalItem;
import com.agricraft.agricraft.common.item.MagnifyingGlassItem;
import com.agricraft.agricraft.common.item.RakeItem;
import com.agricraft.agricraft.common.item.SeedBagItem;
import com.agricraft.agricraft.common.item.TrowelItem;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.PlatformRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public class ModItems {

	public static final PlatformRegistry<Item> ITEMS = Platform.get().createRegistry(Registry.ITEM, AgriApi.MOD_ID);

	public static final PlatformRegistry.Entry<Item> DEBUGGER = ITEMS.register("debugger", () -> new DebuggerItem(new Item.Properties()));

	public static final PlatformRegistry.Entry<Item> JOURNAL = ITEMS.register("journal", () -> new JournalItem(tab()));
	public static final PlatformRegistry.Entry<Item> SEED_ANALYZER = ITEMS.register("seed_analyzer", () -> new BlockItem(ModBlocks.SEED_ANALYZER.get(), tab()));
	public static final PlatformRegistry.Entry<Item> CLIPPER = ITEMS.register("clipper", () -> new ClipperItem(tab().stacksTo(1)));
	public static final PlatformRegistry.Entry<Item> MAGNIFYING_GLASS = ITEMS.register("magnifying_glass", () -> new MagnifyingGlassItem(tab().stacksTo(1)));
	public static final PlatformRegistry.Entry<Item> WOODEN_RAKE = ITEMS.register("wooden_rake", () -> new RakeItem(tab(), RakeItem.WOOD_LOGIC));
	public static final PlatformRegistry.Entry<Item> IRON_RAKE = ITEMS.register("iron_rake", () -> new RakeItem(tab(), RakeItem.IRON_LOGIC));
	public static final PlatformRegistry.Entry<Item> TROWEL = ITEMS.register("trowel", () -> new TrowelItem(tab().stacksTo(1)));
	public static final PlatformRegistry.Entry<Item> WOODEN_CROP_STICKS = ITEMS.register("wooden_crop_sticks", () -> new CropSticksItem(ModBlocks.CROP.get(), CropStickVariant.WOODEN));
	public static final PlatformRegistry.Entry<Item> IRON_CROP_STICKS = ITEMS.register("iron_crop_sticks", () -> new CropSticksItem(ModBlocks.CROP.get(), CropStickVariant.IRON));
	public static final PlatformRegistry.Entry<Item> OBSIDIAN_CROP_STICKS = ITEMS.register("obsidian_crop_sticks", () -> new CropSticksItem(ModBlocks.CROP.get(), CropStickVariant.OBSIDIAN));
	public static final PlatformRegistry.Entry<Item> SEED_BAG = ITEMS.register("seed_bag", () -> new SeedBagItem(tab().stacksTo(1)));
	public static final PlatformRegistry.Entry<Item> SEED = ITEMS.register("seed", () -> Platform.get().createAgriSeedItem(new Item.Properties().tab(ModCreativeTabs.SEED_TAB)));

	// irrigation, ported from AgriCraft 3
	public static final PlatformRegistry.Entry<Item> IRRIGATION_TANK = ITEMS.register("irrigation_tank", () -> new TooltipBlockItem(ModBlocks.IRRIGATION_TANK.get(), tab(), "agricraft.tooltip.tank.l1"));
	public static final PlatformRegistry.Entry<Item> IRRIGATION_CHANNEL = ITEMS.register("irrigation_channel", () -> new TooltipBlockItem(ModBlocks.IRRIGATION_CHANNEL.get(), tab()));
	public static final PlatformRegistry.Entry<Item> IRRIGATION_CHANNEL_HOLLOW = ITEMS.register("irrigation_channel_hollow", () -> new TooltipBlockItem(ModBlocks.IRRIGATION_CHANNEL_HOLLOW.get(), tab()));
	public static final PlatformRegistry.Entry<Item> SPRINKLER = ITEMS.register("sprinkler", () -> new TooltipBlockItem(ModBlocks.SPRINKLER.get(), tab(), "agricraft.tooltip.sprinkler.l1"));
	public static final PlatformRegistry.Entry<Item> CHANNEL_VALVE = ITEMS.register("agri_channel_valve", () -> new ChannelValveItem(tab()));

	public static final PlatformRegistry.Entry<Item> COAL_PEBBLE = ITEMS.register("coal_pebble", () -> new Item(tab()));
	public static final PlatformRegistry.Entry<Item> COPPER_NUGGET = ITEMS.register("copper_nugget", () -> new Item(tab()));
	public static final PlatformRegistry.Entry<Item> DIAMOND_SHARD = ITEMS.register("diamond_shard", () -> new Item(tab()));
	public static final PlatformRegistry.Entry<Item> EMERALD_SHARD = ITEMS.register("emerald_shard", () -> new Item(tab()));
	public static final PlatformRegistry.Entry<Item> QUARTZ_SHARD = ITEMS.register("quartz_shard", () -> new Item(tab()));
	public static final PlatformRegistry.Entry<Item> NETHERITE_SLIVER = ITEMS.register("netherite_sliver", () -> new Item(tab().fireResistant()));
	public static final PlatformRegistry.Entry<Item> AMATHYLLIS_PETAL = ITEMS.register("amathyllis_petal", () -> new Item(tab()));

	private static Item.Properties tab() {
		return new Item.Properties().tab(ModCreativeTabs.MAIN_TAB);
	}

}
