package net.mystcraft.fabric;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.minecraft.exploration.ArchivistShop;
import net.mystcraft.minecraft.exploration.ArchivistTrades;

public final class FabricExplorationHooks {
    private FabricExplorationHooks() {}
    public static void initialize() {
        BiomeModifications.addFeature(BiomeSelectors.all(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation("mystcraft", "small_library")));
        BiomeModifications.addFeature(BiomeSelectors.all(), GenerationStep.Decoration.UNDERGROUND_DECORATION,
                ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation("mystcraft", "crystal_formation")));
        TradeOfferHelper.registerVillagerOffers(ExplorationRegistry.ARCHIVIST.get(), 1, offers -> offers.addAll(ArchivistTrades.pageShop()));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (entity instanceof Villager villager) {
                if (ArchivistShop.canOpen(player, villager)) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        net.mystcraft.integration.network.ArchivistNetwork.open(serverPlayer, villager);
                    }
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
                if (!world.isClientSide) ArchivistShop.ensureOffers(villager);
            }
            return InteractionResult.PASS;
        });
    }
}
