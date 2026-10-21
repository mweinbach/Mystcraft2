package ports.buildcraft.robotics;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.builders.FacingMachineBlock;

/**
 * Upstream {@code BlockZonePlanner}: an iron block that faces the player who placed it and opens its GUI.
 * The wrench turns it, as upstream's facing blocks do from the wrench's first use.
 */
public class ZonePlannerBlock extends FacingMachineBlock {
    public ZonePlannerBlock(Properties properties) {
        super(properties);
    }

    /** Upstream {@code tile.zonePlannerBlock.tip}. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.buildcraft.zone_planner.tip").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZonePlannerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BCRobotics.ZONE_PLANNER_TYPE.get(), (l, p, s, planner) -> planner.serverTick());
    }

    /** The planner is not a container, so hoppers cannot see it; the block itself drops the slots. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ZonePlannerBlockEntity planner) {
            Containers.dropContents(level, pos, planner.inventory());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
