package net.mystcraft.minecraft.exploration;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;
import net.mystcraft.integration.MystcraftItems;

/** Restores the classic three visible rank slots using the modern persistent merchant UI. */
public final class ArchivistShop {
    private ArchivistShop() {}
    public static boolean canOpen(net.minecraft.world.entity.player.Player player,Villager villager){
        return player.level==villager.level&&VillageArchivistPools.settings().archivistEnabled()&&!player.isShiftKeyDown()&&!player.isSpectator()
                &&player.isAlive()&&villager.isAlive()&&!villager.isBaby()
                &&villager.getVillagerData().getProfession()==ExplorationRegistry.ARCHIVIST.get()
                &&(!villager.isTrading()||villager.getTradingPlayer()==player)&&player.distanceToSqr(villager)<=64;
    }
    public static void ensureOffers(Villager villager) {
        if (villager.level.isClientSide || villager.isBaby()
                || villager.getVillagerData().getProfession() != ExplorationRegistry.ARCHIVIST.get()) return;
        MerchantOffers offers = villager.getOffers();
        Set<Integer> present = new HashSet<>();
        for (var offer : offers) {
            int rank = ExplorationPages.rank(offer.getResult(), villager.level.registryAccess());
            if (rank >= 0) present.add(rank);
        }
        var listings = ArchivistTrades.pageShop();
        for (int rank = 1; rank <= 3; rank++) {
            if (present.contains(rank)) continue;
            var offer = listings.get(rank - 1).getOffer(villager, villager.getRandom());
            if (offer != null) offers.add(offer);
        }
        if (offers.stream().noneMatch(offer -> offer.getResult().is(MystcraftItems.BOOSTER.get()))) {
            var booster = ArchivistTrades.booster().getOffer(villager, villager.getRandom());
            if (booster != null) offers.add(booster);
        }
        // Existing offers (including uses, special prices and other mods' offers) are retained.
    }
}
