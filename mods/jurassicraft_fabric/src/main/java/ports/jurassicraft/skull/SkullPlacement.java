package ports.jurassicraft.skull;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

public final class SkullPlacement {
    private SkullPlacement() {}
    public static boolean isFresh(ItemStack stack) { return !stack.isEmpty() && stack.is(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get()); }
    public static boolean isSkull(ItemStack stack) { return isFresh(stack) || !stack.isEmpty() && stack.is(JurassicContent.BONES.get(Species.TYRANNOSAURUS).get("skull").get()); }
    public static boolean hasStand(ItemStack stack) { return !stack.hasTag() || !stack.getTag().contains("Type") || stack.getTag().getBoolean("Type"); }
    public static void appendTooltip(ItemStack stack, java.util.List<Component> tooltip) {
        tooltip.add(Component.translatable(hasStand(stack) ? "message.jurassicraft.skull_stand" : "message.jurassicraft.skull_no_stand").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.jurassicraft.skull_pose").withStyle(ChatFormatting.WHITE));
    }
    public static InteractionResultHolder<ItemStack> toggle(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || player.isSpectator()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            boolean stand=!hasStand(stack); stack.getOrCreateTag().putBoolean("Type",stand);
            player.displayClientMessage(Component.translatable(stand ? "message.jurassicraft.skull_stand" : "message.jurassicraft.skull_no_stand").withStyle(ChatFormatting.YELLOW),true);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    /** Delegate actual placement to vanilla BlockItem so other mods can intercept it normally. */
    public static InteractionResult place(UseOnContext click) {
        ItemStack stack = click.getItemInHand();
        Player player = click.getPlayer();
        Level level = click.getLevel();
        if (player == null || player.isSpectator()) return InteractionResult.PASS;
        BlockPlaceContext context = new BlockPlaceContext(click);
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        if (face == Direction.DOWN || !context.canPlace() || !level.isInWorldBounds(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, face, stack)) return InteractionResult.PASS;
        ItemStack placedStack = stack.copy();
        InteractionResult result = SkullRegistry.placer().place(context);
        if (!level.isClientSide && result.consumesAction() && level.getBlockEntity(pos) instanceof SkullBlockEntity skull) {
            float angle = face == Direction.UP ? (float)(90 - Math.toDegrees(Math.atan2(pos.getZ() + .5 - player.getZ(), pos.getX() + .5 - player.getX())))
                    : face.getAxis() == Direction.Axis.X ? face.toYRot() : 180 + face.toYRot();
            skull.setSkull(placedStack, angle);
        }
        return result;
    }
}
