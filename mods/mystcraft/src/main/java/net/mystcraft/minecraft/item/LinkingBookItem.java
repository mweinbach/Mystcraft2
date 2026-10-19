package net.mystcraft.minecraft.item;

import java.util.List;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class LinkingBookItem extends Item {
    @FunctionalInterface
    public interface BookOpener {
        void open(ServerPlayer player, InteractionHand hand, ItemStack book);
    }

    private final BookOpener opener;

    public LinkingBookItem(Properties properties, BookOpener opener) {
        super(properties.stacksTo(1));
        this.opener = opener;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) opener.open(serverPlayer, hand, book);
        return InteractionResultHolder.sidedSuccess(book, level.isClientSide);
    }

    public boolean hasCustomEntity(ItemStack stack) { return true; }

    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        // Forge also sees our custom ItemEntity subclass when adding it to the level.
        // Returning null in that case keeps the existing entity and prevents replacement loops.
        if (location instanceof LinkBookEntity) return null;
        LinkBookEntity book = new LinkBookEntity(level, location.getX(), location.getY(), location.getZ(), stack.copy());
        book.setDeltaMovement(location.getDeltaMovement());
        book.setYRot(location.getYRot());
        book.setXRot(location.getXRot());
        if (location instanceof ItemEntity item) {
            CompoundTag saved = item.saveWithoutId(new CompoundTag());
            book.setPickUpDelay(saved.getShort("PickupDelay"));
            book.setOwner(item.getOwner());
            book.setThrower(item.getThrower());
        }
        return book;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return net.mystcraft.minecraft.linking.LinkModifiers.from(stack).following(); }

    @Override
    public boolean isBarVisible(ItemStack stack) { return BookHealth.health(stack) < BookHealth.maximum(stack); }

    @Override
    public int getBarWidth(ItemStack stack) { return Math.round(13 * BookHealth.health(stack) / BookHealth.maximum(stack)); }

    @Override
    public int getBarColor(ItemStack stack) {
        return net.minecraft.util.Mth.hsvToRgb(BookHealth.health(stack) / BookHealth.maximum(stack) / 3, 1, 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        LinkDestination.read(stack).ifPresentOrElse(destination -> tooltip.add(
                Component.translatable("tooltip.mystcraft.destination", destination.dimension().location().toString())),
                () -> tooltip.add(Component.translatable("message.mystcraft.invalid_book")));
    }
}
