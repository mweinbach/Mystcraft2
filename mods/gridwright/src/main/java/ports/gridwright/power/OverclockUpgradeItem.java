package ports.gridwright.power;

import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.registry.ModPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Installed by using it on a generator (up to {@link Overclock#MAX}); sneak-use the generator with an
 * empty hand to take them back. Breaking the generator drops them.
 */
public class OverclockUpgradeItem extends Item {

    public OverclockUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gridwright.overclock_upgrade.tooltip", Overclock.MAX)
                .withStyle(ChatFormatting.GRAY));
    }

    /** Sneak-using a generator with upgrades in hand skips the block's own use, so install here too. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        InteractionResult result = use(context.getLevel(), context.getClickedPos(), context.getPlayer(), context.getHand());
        return result != null ? result : InteractionResult.PASS;
    }

    /**
     * Installs upgrades held in {@code hand}, or returns installed ones to a sneaking player with an
     * empty hand. Returns {@code null} when this use is not about upgrades.
     */
    @Nullable
    public static InteractionResult use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator)) return null;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof OverclockUpgradeItem) {
            if (!level.isClientSide) {
                int taken = generator.installOverclock(held.getCount());
                if (!player.getAbilities().instabuild) held.shrink(taken);
                player.displayClientMessage(status(generator), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && player.isSecondaryUseActive() && generator.overclock() > 0) {
            if (!level.isClientSide) {
                ItemStack returned = new ItemStack(ModPower.OVERCLOCK_UPGRADE.get(), generator.removeOverclock());
                if (!player.getInventory().add(returned)) player.drop(returned, false);
                player.displayClientMessage(status(generator), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return null;
    }

    /** Drops a generator's installed upgrades when it is broken. */
    public static void drop(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator && generator.overclock() > 0) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    new ItemStack(ModPower.OVERCLOCK_UPGRADE.get(), generator.removeOverclock()));
        }
    }

    private static Component status(GeneratorBlockEntity generator) {
        return Component.translatable("message.gridwright.overclock", generator.overclock(), Overclock.MAX);
    }
}
