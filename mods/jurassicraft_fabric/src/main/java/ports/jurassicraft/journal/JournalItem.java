package ports.jurassicraft.journal;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import ports.jurassicraft.content.JurassicContent;

/** One item with author variants; journal content can be replaced by resource packs. */
public final class JournalItem extends Item {
    private static final String AUTHOR = "JournalAuthor";
    public JournalItem() { super(new Item.Properties().tab(JurassicContent.TAB)); }
    public static ItemStack create(JournalType type) {
        ItemStack stack = new ItemStack(JournalRegistry.JOURNAL.get());
        stack.getOrCreateTag().putString(AUTHOR, type.id());
        return stack;
    }
    public static JournalType type(ItemStack stack) {
        return JournalType.byId(stack.hasTag() ? stack.getTag().getString(AUTHOR) : "");
    }
    @Override public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        if (allowedIn(tab)) for (JournalType type : JournalType.values()) items.add(create(type));
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("journal.jurassicraft." + type(stack).id()));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer server) JournalRegistry.open(server, type(stack));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
