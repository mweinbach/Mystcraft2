package com.agricraft.agricraft.compat.jade;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.common.block.CropBlock;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.block.irrigation.IrrigationTankBlock;
import com.agricraft.agricraft.compat.AgriTooltipLines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Upstream's Jade plugin (crops and soils), plus the irrigation provider from AgriCraft 3. Jade 8 for 1.19 finds it by the
 * {@code jade} entrypoint on Fabric and by the annotation on Forge. Every line reads client-side block entity data that
 * AgriCraft already syncs, so no server data provider is needed.
 */
@WailaPlugin
public class AgriCraftJadePlugin implements IWailaPlugin {

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(CropBlockComponentProvider.INSTANCE, CropBlock.class);
		registration.registerBlockComponent(SoilComponentProvider.INSTANCE, Block.class);
		registration.registerBlockComponent(IrrigationComponentProvider.INSTANCE, IrrigationTankBlock.class);
		registration.registerBlockComponent(IrrigationComponentProvider.INSTANCE, IrrigationChannelBlock.class);
	}

	public static class CropBlockComponentProvider implements IBlockComponentProvider {

		public static final CropBlockComponentProvider INSTANCE = new CropBlockComponentProvider();
		private static final ResourceLocation ID = new ResourceLocation(AgriApi.MOD_ID, "crop_block");

		private CropBlockComponentProvider() {
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			if (accessor.getBlockEntity() instanceof AgriCrop crop) {
				AgriTooltipLines.crop(crop, accessor.getPlayer().isShiftKeyDown(), tooltip::add);
			}
		}

		@Override
		public ResourceLocation getUid() {
			return ID;
		}

	}

	public static class SoilComponentProvider implements IBlockComponentProvider {

		public static final SoilComponentProvider INSTANCE = new SoilComponentProvider();
		private static final ResourceLocation ID = new ResourceLocation(AgriApi.MOD_ID, "soil");

		private SoilComponentProvider() {
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			AgriTooltipLines.soil(accessor.getLevel(), accessor.getPosition(), accessor.getPlayer().isShiftKeyDown(), tooltip::add);
		}

		@Override
		public ResourceLocation getUid() {
			return ID;
		}

	}

	public static class IrrigationComponentProvider implements IBlockComponentProvider {

		public static final IrrigationComponentProvider INSTANCE = new IrrigationComponentProvider();
		private static final ResourceLocation ID = new ResourceLocation(AgriApi.MOD_ID, "irrigation");

		private IrrigationComponentProvider() {
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			if (accessor.getBlockEntity() instanceof IrrigationComponentBlockEntity component) {
				AgriTooltipLines.irrigation(component, tooltip::add);
			}
		}

		@Override
		public ResourceLocation getUid() {
			return ID;
		}

	}

}
