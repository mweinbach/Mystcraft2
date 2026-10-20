package ports.buildcraft.silicon;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code BlockLaserTable}: a nine-pixel-high table that lasers target, opening its GUI when used and
 * dropping everything but its phantom slots when broken.
 */
public class LaserTableBlock extends BaseEntityBlock implements LaserTargetBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);
    private final BlockEntityType.BlockEntitySupplier<? extends LaserTableBlockEntity> factory;
    private final boolean hasTip;

    public LaserTableBlock(Properties properties, BlockEntityType.BlockEntitySupplier<? extends LaserTableBlockEntity> factory, boolean hasTip) {
        super(properties);
        this.factory = factory;
        this.hasTip = hasTip;
    }

    /** Upstream {@code ItemBlockBC_Neptune} showed the {@code .tip} line when the language had one. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        if (hasTip) tooltip.add(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LaserTableBlockEntity table) {
            Platform.openMenu((ServerPlayer) player, table, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LaserTableBlockEntity table) {
            for (ItemStack stack : table.drops()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (l, p, s, blockEntity) -> {
            if (blockEntity instanceof LaserTableBlockEntity table) table.serverTick();
        };
    }
}
