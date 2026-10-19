package net.mystcraft.minecraft.item;

import java.util.function.Supplier;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class UnlinkedLinkBookItem extends Item {
    private final Supplier<Item> linkingBook;

    public UnlinkedLinkBookItem(Properties properties, Supplier<Item> linkingBook) {
        super(properties.stacksTo(1));
        this.linkingBook = linkingBook;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack bound = new ItemStack(linkingBook.get());
            if (held.hasTag()) bound.setTag(held.getTag().copy());
            LinkDestination.at(serverPlayer).write(bound);
            net.mystcraft.minecraft.linking.LinkingRules.stampBook(serverPlayer.server,serverPlayer.level.dimension(),bound);
            player.setItemInHand(hand, bound);
            player.displayClientMessage(Component.translatable("message.mystcraft.book_bound"), true);
            return InteractionResultHolder.success(bound);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
}
