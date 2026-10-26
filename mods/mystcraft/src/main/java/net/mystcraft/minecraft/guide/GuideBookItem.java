package net.mystcraft.minecraft.guide;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A read-only guide. Its client opener is installed only on physical clients. */
public final class GuideBookItem extends Item {
    private static Runnable clientOpener = () -> {};

    public GuideBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void installClientOpener(Runnable opener) {
        clientOpener = Objects.requireNonNull(opener);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) clientOpener.run();
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("tooltip.mystcraft.guide_book"));
    }
}
