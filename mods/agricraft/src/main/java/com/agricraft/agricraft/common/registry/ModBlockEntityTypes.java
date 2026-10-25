package com.agricraft.agricraft.common.registry;

import net.minecraft.core.Registry;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import com.agricraft.agricraft.common.block.entity.SeedAnalyzerBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationChannelBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.SprinklerBlockEntity;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.PlatformRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntityTypes {
	public static final PlatformRegistry<BlockEntityType<?>> BLOCK_ENTITY_TYPES = Platform.get().createRegistry(Registry.BLOCK_ENTITY_TYPE, AgriApi.MOD_ID);

	public static final PlatformRegistry.Entry<BlockEntityType<CropBlockEntity>> CROP = BLOCK_ENTITY_TYPES.register("crop", () -> BlockEntityType.Builder.of((blockPos, blockState) -> Platform.get().createCropBlockEntity(blockPos, blockState), ModBlocks.CROP.get()).build(null));
	public static final PlatformRegistry.Entry<BlockEntityType<SeedAnalyzerBlockEntity>> SEED_ANALYZER = BLOCK_ENTITY_TYPES.register("seed_analyzer", () -> BlockEntityType.Builder.of(SeedAnalyzerBlockEntity::new, ModBlocks.SEED_ANALYZER.get()).build(null));
	public static final PlatformRegistry.Entry<BlockEntityType<IrrigationTankBlockEntity>> IRRIGATION_TANK = BLOCK_ENTITY_TYPES.register("irrigation_tank", () -> BlockEntityType.Builder.of(IrrigationTankBlockEntity::new, ModBlocks.IRRIGATION_TANK.get()).build(null));
	public static final PlatformRegistry.Entry<BlockEntityType<IrrigationChannelBlockEntity>> IRRIGATION_CHANNEL = BLOCK_ENTITY_TYPES.register("irrigation_channel", () -> BlockEntityType.Builder.of(IrrigationChannelBlockEntity::new, ModBlocks.IRRIGATION_CHANNEL.get(), ModBlocks.IRRIGATION_CHANNEL_HOLLOW.get()).build(null));
	public static final PlatformRegistry.Entry<BlockEntityType<SprinklerBlockEntity>> SPRINKLER = BLOCK_ENTITY_TYPES.register("sprinkler", () -> BlockEntityType.Builder.of(SprinklerBlockEntity::new, ModBlocks.SPRINKLER.get()).build(null));

}
