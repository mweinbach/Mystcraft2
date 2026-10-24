package com.agricraft.agricraft.client;

import com.agricraft.agricraft.client.ber.IrrigationChannelRenderer;
import com.agricraft.agricraft.client.ber.IrrigationTankRenderer;
import com.agricraft.agricraft.client.ber.SprinklerRenderer;
import com.agricraft.agricraft.common.block.entity.irrigation.SprinklerBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * Loader-neutral client registration for the irrigation blocks.
 */
public final class IrrigationClient {

	/** Models that are not referenced by a block state and must be loaded explicitly. */
	public static final List<ResourceLocation> EXTRA_MODELS = List.of(IrrigationChannelRenderer.MODEL_HANDWHEEL);

	private IrrigationClient() {
	}

	public interface RendererSink {
		<T extends BlockEntity> void register(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider);
	}

	public static void init() {
		SprinklerBlockEntity.PARTICLE_SETTING = () -> Minecraft.getInstance().options.particles().get().getId();
	}

	public static void registerRenderers(RendererSink sink) {
		sink.register(ModBlockEntityTypes.IRRIGATION_TANK.get(), IrrigationTankRenderer::new);
		sink.register(ModBlockEntityTypes.IRRIGATION_CHANNEL.get(), IrrigationChannelRenderer::new);
		sink.register(ModBlockEntityTypes.SPRINKLER.get(), SprinklerRenderer::new);
	}

	public static List<Block> cutoutBlocks() {
		return List.of(ModBlocks.IRRIGATION_TANK.get(), ModBlocks.IRRIGATION_CHANNEL.get(), ModBlocks.IRRIGATION_CHANNEL_HOLLOW.get(), ModBlocks.SPRINKLER.get());
	}

}
