package net.mystcraft.minecraft.exploration;

import java.util.List;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.writing.PageData;

/** Modern merchant adapter for the active classic three-rank page shop; profession wiring is separate. */
public final class ArchivistTrades {
    public static final long CLASSIC_RESTOCK_TICKS = 12000;
    private ArchivistTrades() {}
    public static List<VillagerTrades.ItemListing> pageShop() { return List.of(rank(1), rank(2), rank(3), booster()); }
    public static VillagerTrades.ItemListing booster() {
        return (merchant, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, 20),
                new ItemStack(MystcraftItems.BOOSTER.get()), 5, 1, 0);
    }
    private static VillagerTrades.ItemListing rank(int rank) {
        return (merchant, random) -> {
            ItemStack page = ExplorationPages.sampleRank(merchant.level.registryAccess(), random, rank, rank);
            if (page.isEmpty()) return null;
            return new MerchantOffer(new ItemStack(Items.EMERALD, 4 * (1 + rank)), page, 3, 1, 0);
        };
    }
}
