package ports.jurassicraft.platform;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Shared behavior for a loader-provided spawn egg. Forge must construct eggs before entity types
 * exist, so each loader supplies the {@link SpawnEggItem} subclass and forwards these calls.
 */
public interface SpawnEggBehavior {
    InteractionResult useOn(SpawnEggItem egg, UseOnContext context);
    InteractionResultHolder<ItemStack> use(SpawnEggItem egg, Level level, Player player, InteractionHand hand);
    void appendHoverText(SpawnEggItem egg, ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag);
}
