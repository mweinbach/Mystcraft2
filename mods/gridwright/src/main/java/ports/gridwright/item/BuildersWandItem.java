package ports.gridwright.item;

import ports.gridwright.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Right-click a block face to place copies of that block on up to {@link #LIMIT} connected blocks of
 * the same type in the same plane, taking the blocks from the inventory (creative players need none).
 * Each copy is placed exactly as if the player had right-clicked that face with the block: through
 * {@code ItemStack.useOn}, so Forge fires its place event (which claim mods cancel) and vanilla
 * spawn-protection, world-border and adventure-mode rules apply; on Fabric, which has no place event,
 * {@link Platform#mayUseBlock} first asks the claim mods' use-block hook about each support block.
 */
public class BuildersWandItem extends Item {

    public static final int LIMIT = 9;

    public BuildersWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        return build(player, context.getHand(), context.getClickedPos(), context.getClickedFace(), LIMIT) > 0
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Places up to {@code limit} blocks; returns how many were placed. */
    public static int build(ServerPlayer player, InteractionHand hand, BlockPos origin, Direction face, int limit) {
        ServerLevel level = player.getLevel();
        Block block = level.getBlockState(origin).getBlock();
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) return 0;
        boolean creative = player.getAbilities().instabuild;
        int available = creative ? limit : count(player.getInventory(), item);
        int placed = 0;
        for (BlockPos support : WandSurface.find(level, origin, face, Math.min(limit, available), pos -> true)) {
            ItemStack source = creative ? new ItemStack(item) : find(player.getInventory(), item);
            if (source.isEmpty()) break;
            BlockPos target = support.relative(face);
            if (!level.mayInteract(player, target) || !level.getWorldBorder().isWithinBounds(target)
                    || !player.mayUseItemAt(target, face, source)) continue;
            Vec3 click = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            BlockHitResult hit = new BlockHitResult(click, face, support, false);
            if (!Platform.mayUseBlock(player, hand, hit)) continue;
            if (source.useOn(new BlockPlaceContext(level, player, hand, source, hit)).consumesAction()) placed++;
        }
        return placed;
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static ItemStack find(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.builders_wand", LIMIT));
    }
}
