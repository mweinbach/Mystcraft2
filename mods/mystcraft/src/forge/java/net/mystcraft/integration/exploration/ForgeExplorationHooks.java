package net.mystcraft.integration.exploration;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.npc.Villager;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.minecraft.exploration.ArchivistShop;
import net.mystcraft.minecraft.exploration.ArchivistTrades;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ForgeExplorationHooks {
    private static final DeferredRegister<Codec<? extends BiomeModifier>> MODIFIERS = DeferredRegister.create(
            ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "mystcraft");
    static { MODIFIERS.register("exploration", () -> ExplorationBiomeModifier.CODEC); }
    private ForgeExplorationHooks() {}
    public static void initialize(IEventBus bus) {
        MODIFIERS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(ForgeExplorationHooks::trades);
        MinecraftForge.EVENT_BUS.addListener(ForgeExplorationHooks::interact);
    }
    private static void trades(VillagerTradesEvent event) {
        if (event.getType() == ExplorationRegistry.ARCHIVIST.get()) event.getTrades().get(1).addAll(ArchivistTrades.pageShop());
    }
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (ArchivistShop.canOpen(event.getEntity(), villager)) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                net.mystcraft.integration.network.ArchivistNetwork.open(player, villager);
            }
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        } else if (!event.getLevel().isClientSide) ArchivistShop.ensureOffers(villager);
    }
}
