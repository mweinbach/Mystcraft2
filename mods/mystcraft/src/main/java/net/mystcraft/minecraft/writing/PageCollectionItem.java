package net.mystcraft.minecraft.writing;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Portable page storage. Folders preserve writing order; portfolios hold a collection. */
public final class PageCollectionItem extends Item {
    public static final int MAX_PAGES = 256;
    private final boolean ordered;
    public PageCollectionItem(Properties properties, boolean ordered) {
        super(properties.stacksTo(1));
        this.ordered = ordered;
    }
    public boolean ordered() { return ordered; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack collection = player.getItemInHand(hand);
        if (collection.getCount() != 1) return InteractionResultHolder.fail(collection);
        if (player instanceof ServerPlayer serverPlayer)
            GameplayPlatform.openItemMenu(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, owner) -> new PageCollectionMenu(id, inventory, hand), collection.getHoverName()), hand);
        return InteractionResultHolder.sidedSuccess(collection, level.isClientSide);
    }
    public static boolean isCollection(ItemStack stack) { return stack.getItem() instanceof PageCollectionItem; }
    public static List<CompoundTag> pages(ItemStack stack) {
        return isCollection(stack) && stack.hasTag() ? PageData.readPages(stack.getTag()) : List.of();
    }
    public static void setPages(ItemStack stack, List<CompoundTag> pages) {
        if (!isCollection(stack) || pages.size() > MAX_PAGES) throw new IllegalArgumentException("Invalid page collection");
        PageData.writePages(stack.getOrCreateTag(), pages);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("tooltip.mystcraft.collection_pages", pages(stack).size(), MAX_PAGES));
        tooltip.add(Component.translatable("tooltip.mystcraft.collection_desk"));
    }
}
