package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ports.gridwright.block.GridMemberBlock;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.registry.ModPower;

import javax.annotation.Nullable;

/**
 * A fire, lava, manual or dragon egg mill. Using it shows its output and its grid's totals; using a
 * manual mill also cranks it (hold use to keep it turning).
 */
public class MillBlock extends GridMemberBlock {

    private final MillKind kind;

    public MillBlock(MillKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public MillKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MillBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModPower.MILL.get(), MillBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MillBlockEntity mill) {
            if (kind == MillKind.MANUAL && !mill.crank(player)) {
                player.displayClientMessage(Component.translatable("message.gridwright.manual_mill.refused"), true);
                return InteractionResult.CONSUME;
            }
            double output = kind == MillKind.MANUAL ? MillKind.MANUAL_GP : mill.output();
            player.displayClientMessage(Component.translatable("message.gridwright.producer.output",
                    GridBlockEntity.format(output)).append(" | ").append(mill.gridStatus(server.getServer())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
