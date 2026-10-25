package com.agricraft.agricraft.common.util.forge;

import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import com.agricraft.agricraft.common.forge.block.entity.ForgeCropBlockEntity;
import com.agricraft.agricraft.common.item.AgriSeedItem;
import com.agricraft.agricraft.common.item.forge.ForgeAgriSeedItem;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.common.util.ExtraDataMenuProvider;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.PlatformRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;

/**
 * Forge implementation of {@link Platform}
 */
public class ForgePlatform extends Platform {

	@Override
	public <T> PlatformRegistry<T> createRegistry(Registry<T> registry, String modid) {
		return new ForgeRegistry<>(registry, modid);
	}

	@Override
	public AgriSeedItem createAgriSeedItem(Item.Properties properties) {
		return new ForgeAgriSeedItem(properties);
	}

	@Override
	public CropBlockEntity createCropBlockEntity(BlockPos pos, BlockState state) {
		return new ForgeCropBlockEntity(pos, state);
	}

	@Override
	public CreativeModeTab createMainCreativeTab() {
		// 1.19.2 tabs are constructed directly; the label maps to "itemGroup.agricraft.main".
		return new CreativeModeTab("agricraft.main") {
			@Override
			public ItemStack makeIcon() {
				return new ItemStack(ModItems.DEBUGGER.get());
			}
		};
	}

	@Override
	public CreativeModeTab createSeedsCreativeTab() {
		return new CreativeModeTab("agricraft.seeds") {
			@Override
			public ItemStack makeIcon() {
				return new ItemStack(Items.WHEAT_SEEDS);
			}
		};
	}

	@Override
	public Optional<RegistryAccess> getRegistryAccess() {
		if (FMLLoader.getDist().isClient()) {
			if (Minecraft.getInstance().level != null) {
				return Optional.of(Minecraft.getInstance().level.registryAccess());
			}
		} else {
			if (ServerLifecycleHooks.getCurrentServer() != null) {
				return Optional.of(ServerLifecycleHooks.getCurrentServer().registryAccess());
			}
		}
		return Optional.empty();
	}

	@Override
	public List<Item> getItemsFromLocation(ExtraCodecs.TagOrElementLocation tag) {
		if (!tag.tag()) {
			return List.of(ForgeRegistries.ITEMS.getValue(tag.id()));
		} else {
			return ForgeRegistries.ITEMS.tags().getTag(ItemTags.create(tag.id())).stream().toList();
		}
	}

	@Override
	public List<Block> getBlocksFromLocation(ExtraCodecs.TagOrElementLocation tag) {
		if (!tag.tag()) {
			return List.of(ForgeRegistries.BLOCKS.getValue(tag.id()));
		} else {
			return ForgeRegistries.BLOCKS.tags().getTag(BlockTags.create(tag.id())).stream().toList();
		}
	}

	@Override
	public List<Fluid> getFluidsFromLocation(ExtraCodecs.TagOrElementLocation tag) {
		if (!tag.tag()) {
			return List.of(ForgeRegistries.FLUIDS.getValue(tag.id()));
		} else {
			return ForgeRegistries.FLUIDS.tags().getTag(FluidTags.create(tag.id())).stream().toList();
		}
	}

	@Override
	public <T extends AbstractContainerMenu> MenuType<T> createMenuType(Platform.MenuFactory<T> factory) {
		return IForgeMenuType.create(factory::create);
	}

	@Override
	public void openMenu(ServerPlayer player, ExtraDataMenuProvider provider) {
		NetworkHooks.openScreen(player, provider, buf -> provider.writeExtraData(player, buf));
	}

	@Override
	public ParticleType<?> getParticleType(ResourceLocation particleId) {
		return ForgeRegistries.PARTICLE_TYPES.getValue(particleId);
	}

}
