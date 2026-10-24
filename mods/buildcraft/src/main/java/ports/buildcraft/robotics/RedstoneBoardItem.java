package ports.buildcraft.robotics;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ports.buildcraft.robotics.board.RobotBoardType;

/**
 * A redstone board, written for this port. The blank board is programmed into one of the {@link RobotBoardType}s in
 * the assembly table; a programmed board goes into a robot through the crafting grid ({@link RobotBoardRecipe}) or
 * by using it on a deployed robot.
 */
public class RedstoneBoardItem extends Item {
    @Nullable
    public final RobotBoardType type;

    public RedstoneBoardItem(Properties properties, @Nullable RobotBoardType type) {
        super(properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (type == null) tooltip.add(Component.translatable("item.buildcraft.redstone_board.blank").withStyle(ChatFormatting.GRAY));
        else tooltip.add(type.description().copy().withStyle(ChatFormatting.GRAY));
    }
}
