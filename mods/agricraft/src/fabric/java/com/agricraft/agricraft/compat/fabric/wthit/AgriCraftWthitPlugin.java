package com.agricraft.agricraft.compat.fabric.wthit;

import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.common.block.CropBlock;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import com.agricraft.agricraft.common.block.irrigation.IrrigationTankBlock;
import com.agricraft.agricraft.compat.AgriTooltipLines;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.world.level.block.Block;

/**
 * Upstream's WTHIT plugin (crops and soils) plus AgriCraft 3's irrigation lines, for WTHIT 5 on Fabric. WTHIT 5 reads
 * plugins from the {@code waila:plugins} entry in {@code fabric.mod.json}; upstream's {@code wthit_plugins.json} is WTHIT
 * 6+. The lines come from {@link AgriTooltipLines}, shared with the Jade plugin.
 */
public class AgriCraftWthitPlugin implements IWailaPlugin {

	@Override
	public void register(IRegistrar registrar) {
		registrar.addComponent(CropBlockComponentProvider.INSTANCE, TooltipPosition.BODY, CropBlock.class);
		registrar.addComponent(SoilComponentProvider.INSTANCE, TooltipPosition.BODY, Block.class);
		registrar.addComponent(IrrigationComponentProvider.INSTANCE, TooltipPosition.BODY, IrrigationTankBlock.class);
		registrar.addComponent(IrrigationComponentProvider.INSTANCE, TooltipPosition.BODY, IrrigationChannelBlock.class);
	}

	public static class CropBlockComponentProvider implements IBlockComponentProvider {

		public static final CropBlockComponentProvider INSTANCE = new CropBlockComponentProvider();

		@Override
		public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
			if (accessor.getBlockEntity() instanceof AgriCrop crop) {
				AgriTooltipLines.crop(crop, accessor.getPlayer().isShiftKeyDown(), tooltip::addLine);
			}
		}

	}

	public static class SoilComponentProvider implements IBlockComponentProvider {

		public static final SoilComponentProvider INSTANCE = new SoilComponentProvider();

		@Override
		public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
			AgriTooltipLines.soil(accessor.getWorld(), accessor.getPosition(), accessor.getPlayer().isShiftKeyDown(), tooltip::addLine);
		}

	}

	public static class IrrigationComponentProvider implements IBlockComponentProvider {

		public static final IrrigationComponentProvider INSTANCE = new IrrigationComponentProvider();

		@Override
		public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
			if (accessor.getBlockEntity() instanceof IrrigationComponentBlockEntity component) {
				AgriTooltipLines.irrigation(component, tooltip::addLine);
			}
		}

	}

}
