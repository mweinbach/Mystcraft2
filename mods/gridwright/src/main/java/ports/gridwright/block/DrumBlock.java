package ports.gridwright.block;

import ports.gridwright.platform.Platform;
import ports.gridwright.storage.DrumBlockEntity;
import ports.gridwright.storage.FluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A single-fluid tank. Buckets and other fluid containers fill and empty it by hand; pipes use it
 * through each loader's fluid API. Broken drums keep their fluid (the loot table copies it).
 */
public class DrumBlock extends BaseEntityBlock {

    private final DrumTier tier;

    public DrumBlock(DrumTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public DrumTier tier() {
        return tier;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrumBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            // The server decides; claim the click for anything that might be a fluid container.
            return held.isEmpty() || held.getItem() instanceof BlockItem ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!held.isEmpty() && Platform.interactWithFluidStorage(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && level.getBlockEntity(pos) instanceof DrumBlockEntity drum) {
            player.displayClientMessage(drum.tank().describe(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DrumBlockEntity drum ? drum.signal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        FluidTank tank = new FluidTank(tier.buckets(), () -> { });
        CompoundTag blockEntity = BlockItem.getBlockEntityData(stack);
        if (blockEntity != null) tank.load(blockEntity.getCompound(DrumBlockEntity.TANK));
        tooltip.add(tank.describe());
    }
}
