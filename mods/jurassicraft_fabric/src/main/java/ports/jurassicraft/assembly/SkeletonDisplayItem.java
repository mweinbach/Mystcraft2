package ports.jurassicraft.assembly;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import ports.jurassicraft.content.Species;

public final class SkeletonDisplayItem extends BlockItem {
    public SkeletonDisplayItem(Block block, Properties properties) { super(block, properties); }
    public static ItemStack create(Species species) {
        return create(species, false);
    }
    public static ItemStack create(Species species, boolean fresh) {
        ItemStack result = new ItemStack(AssemblyRegistry.DISPLAY_ITEM.get());
        CompoundTag data = new CompoundTag();
        data.putString("Species", species.id());
        data.putBoolean("Fresh", fresh);
        BlockItem.setBlockEntityData(result, AssemblyRegistry.DISPLAY_ENTITY.get(), data);
        return result;
    }
    public static Species species(ItemStack stack) {
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        return SkeletonDisplayBlockEntity.parseSpecies(data == null ? "" : data.getString("Species"));
    }
    public static boolean fresh(ItemStack stack) {
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        return data != null && data.getBoolean("Fresh");
    }
    private static int mode(ItemStack stack, String key, int max) {
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        return data == null ? 0 : Math.max(0, Math.min(data.getInt(key), max));
    }
    private static final String[] GENDERS = {"random", "male", "female"};
    private static final String[] POSES = {"idle", "death", "attacking"};
    @Override public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        if (allowedIn(tab)) for (Species species : Species.values()) {
            items.add(create(species, false));
            items.add(create(species, true));
        }
    }
    @Override public Component getName(ItemStack stack) {
        return Component.translatable(fresh(stack) ? "item.jurassicraft.fresh_skeleton" : "item.jurassicraft.fossil_skeleton",
                Component.translatable("entity.jurassicraft." + species(stack).id()));
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jurassicraft.skeleton_gender", Component.translatable("mode.jurassicraft." + GENDERS[mode(stack, "Gender", 2)])).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.jurassicraft.skeleton_change_gender").withStyle(ChatFormatting.GRAY));
        if (species(stack) == Species.TYRANNOSAURUS) {
            tooltip.add(Component.translatable("tooltip.jurassicraft.skeleton_pose", Component.translatable("pose.jurassicraft." + POSES[mode(stack, "Pose", 2)])).withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.jurassicraft.skeleton_change_pose").withStyle(ChatFormatting.GRAY));
        }
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide) {
                ItemStack stack = context.getItemInHand();
                int gender = (mode(stack, "Gender", 2) + 1) % 3;
                stack.getOrCreateTagElement("BlockEntityTag").putInt("Gender", gender);
                player.displayClientMessage(Component.translatable("tooltip.jurassicraft.skeleton_gender", Component.translatable("mode.jurassicraft." + GENDERS[gender])), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && species(stack) == Species.TYRANNOSAURUS) {
            if (!level.isClientSide) {
                int pose = (mode(stack, "Pose", 2) + 1) % 3;
                stack.getOrCreateTagElement("BlockEntityTag").putInt("Pose", pose);
                player.displayClientMessage(Component.translatable("tooltip.jurassicraft.skeleton_pose", Component.translatable("pose.jurassicraft." + POSES[pose])), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }
}
