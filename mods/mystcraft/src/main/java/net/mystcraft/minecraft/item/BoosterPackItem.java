package net.mystcraft.minecraft.item;

import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.exploration.ExplorationPages;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;

/** Original booster distribution: seven rank-0, four rank-1, four rank-2, one rank-3+. */
public final class BoosterPackItem extends Item {
    public BoosterPackItem(Properties properties) { super(properties); }
    public static ItemStack generate(RandomSource random) { return generate(null, random); }
    public static ItemStack generate(net.minecraft.core.RegistryAccess registries, RandomSource random) {
        var pages = new ArrayList<CompoundTag>();
        int[] counts = {7, 4, 4, 1};
        for (int rank = 0; rank < counts.length; rank++) for (int i = 0; i < counts[rank]; i++) {
            ItemStack page = ExplorationPages.sampleRank(registries, random, rank, rank == 3 ? Integer.MAX_VALUE : rank);
            if (!page.isEmpty()) pages.add(PageData.payload(page));
        }
        if (pages.isEmpty()) return ItemStack.EMPTY;
        ItemStack folder = new ItemStack(WritingDeskRegistry.FOLDER.get());
        PageCollectionItem.setPages(folder, pages);
        return folder;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(pack);
        // A folder is nonstackable: preflight before any generation or inventory changes.
        if (pack.getCount() > 1 && player.getInventory().getFreeSlot() < 0) return InteractionResultHolder.fail(pack);
        ItemStack folder = generate(level.registryAccess(), player.getRandom());
        if (folder.isEmpty()) return InteractionResultHolder.fail(pack);
        if (pack.getCount() == 1) return InteractionResultHolder.success(folder);
        if (!player.getInventory().add(folder)) return InteractionResultHolder.fail(pack);
        pack.shrink(1);
        player.getInventory().setChanged();
        return InteractionResultHolder.success(pack);
    }
}
