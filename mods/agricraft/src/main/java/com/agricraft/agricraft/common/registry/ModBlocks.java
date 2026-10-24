package com.agricraft.agricraft.common.registry;

import net.minecraft.core.Registry;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.block.CropBlock;
import com.agricraft.agricraft.common.block.SeedAnalyzerBlock;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.block.irrigation.IrrigationTankBlock;
import com.agricraft.agricraft.common.block.irrigation.SprinklerBlock;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.PlatformRegistry;
import net.minecraft.world.level.block.Block;

public class ModBlocks {
	public static final PlatformRegistry<Block> BLOCKS = Platform.get().createRegistry(Registry.BLOCK, AgriApi.MOD_ID);

	public static final PlatformRegistry.Entry<Block> CROP = BLOCKS.register("crop", CropBlock::new);
	public static final PlatformRegistry.Entry<Block> SEED_ANALYZER = BLOCKS.register("seed_analyzer", SeedAnalyzerBlock::new);

	// irrigation, ported from AgriCraft 3
	public static final PlatformRegistry.Entry<Block> IRRIGATION_TANK = BLOCKS.register("irrigation_tank", IrrigationTankBlock::new);
	public static final PlatformRegistry.Entry<Block> IRRIGATION_CHANNEL = BLOCKS.register("irrigation_channel", () -> new IrrigationChannelBlock(false));
	public static final PlatformRegistry.Entry<Block> IRRIGATION_CHANNEL_HOLLOW = BLOCKS.register("irrigation_channel_hollow", () -> new IrrigationChannelBlock(true));
	public static final PlatformRegistry.Entry<Block> SPRINKLER = BLOCKS.register("sprinkler", SprinklerBlock::new);

}
