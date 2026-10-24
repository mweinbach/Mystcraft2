package ports.gridwright.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Right-click a block face (main hand) to break up to {@link #LIMIT} connected blocks of the same type
 * in that plane whose clicked side is open. Each block is broken by the player's own
 * {@code ServerPlayerGameMode.destroyBlock}, exactly like mining it, so Forge's break event and Fabric's
 * {@code PlayerBlockBreakEvents} fire (claim mods cancel them) and adventure mode, spawn protection and
 * game-master blocks are respected. Blocks drop as if mined with an iron tool. Unbreakable blocks,
 * blocks harder than {@link #MAX_HARDNESS} and blocks with block entities (chests, machines) are left
 * alone.
 */
public class DestructionWandItem extends Item {

    public static final int LIMIT = 9;
    /** Iron blocks (5) break; obsidian (50) and harder do not. */
    public static final float MAX_HARDNESS = 5.0F;

    public DestructionWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        return destroy(player, context.getClickedPos(), context.getClickedFace(), LIMIT) > 0
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Breaks up to {@code limit} blocks as {@code player}; returns how many broke. */
    public static int destroy(ServerPlayer player, BlockPos origin, Direction face, int limit) {
        ServerLevel level = player.getLevel();
        int broken = 0;
        for (BlockPos pos : WandSurface.find(level, origin, face, limit, pos -> breakable(level, pos))) {
            if (level.mayInteract(player, pos) && player.gameMode.destroyBlock(pos)) broken++;
        }
        return broken;
    }

    public static boolean breakable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        return !state.isAir() && !state.hasBlockEntity() && hardness >= 0 && hardness <= MAX_HARDNESS;
    }

    /** Harvests what an iron pickaxe, shovel, axe or hoe would. */
    @Override
    public boolean isCorrectToolForDrops(BlockState state) {
        return !state.is(BlockTags.NEEDS_DIAMOND_TOOL) && (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_HOE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.destruction_wand", LIMIT));
    }
}
