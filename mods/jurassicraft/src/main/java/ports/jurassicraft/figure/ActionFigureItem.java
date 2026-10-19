package ports.jurassicraft.figure;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import ports.jurassicraft.content.Species;

public final class ActionFigureItem extends BlockItem {
    private static final String[] GENDER = {"random", "male", "female"};
    public ActionFigureItem(Block block, Properties properties) { super(block, properties); }
    /** A new figure stack with the requested dinosaur, suitable for loot tables. */
    public static ItemStack create(Species species) {
        ItemStack stack = new ItemStack(ActionFigureRegistry.ITEM.get());
        CompoundTag data = new CompoundTag(); data.putString("Species", species.id());
        BlockItem.setBlockEntityData(stack, ActionFigureRegistry.ENTITY.get(), data);
        return stack;
    }
    public static Species species(ItemStack stack) {
        CompoundTag data = getBlockEntityData(stack);
        return ActionFigureBlockEntity.parseSpecies(data == null ? "" : data.getString("Species"));
    }
    public static int gender(ItemStack stack) {
        CompoundTag data = getBlockEntityData(stack);
        return data == null ? 0 : Math.max(0, Math.min(2, data.getInt("Gender")));
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable("item.jurassicraft.action_figure", Component.translatable("entity.jurassicraft." + species(stack).id()));
    }
    @Override public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        if (allowedIn(tab)) for (Species species : Species.values()) items.add(create(species));
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jurassicraft.skeleton_gender", Component.translatable("mode.jurassicraft." + GENDER[gender(stack)])).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.jurassicraft.skeleton_change_gender").withStyle(ChatFormatting.GRAY));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide) {
                ItemStack stack = context.getItemInHand();
                int mode = (gender(stack) + 1) % 3;
                stack.getOrCreateTagElement("BlockEntityTag").putInt("Gender", mode);
                player.displayClientMessage(Component.translatable("tooltip.jurassicraft.skeleton_gender", Component.translatable("mode.jurassicraft." + GENDER[mode])), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
}
