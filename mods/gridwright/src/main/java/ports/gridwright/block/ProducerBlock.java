package ports.gridwright.block;

import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.grid.ProducerKind;
import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** A solar or lunar panel, water mill or wind mill. Using it shows its output and its grid's totals. */
public class ProducerBlock extends GridMemberBlock {

    private static final VoxelShape PANEL = Block.box(0, 0, 0, 16, 6, 16);

    private final ProducerKind kind;

    public ProducerBlock(ProducerKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public ProducerKind kind() {
        return kind;
    }

    private boolean isPanel() {
        return kind == ProducerKind.SOLAR_PANEL || kind == ProducerKind.LUNAR_PANEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isPanel() ? PANEL : Shapes.block();
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return isPanel();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProducerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.PRODUCER.get(), ProducerBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ProducerBlockEntity producer) {
            player.displayClientMessage(Component.translatable("message.gridwright.producer.output",
                    GridBlockEntity.format(producer.output())).append(" | ").append(producer.gridStatus(server.getServer())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
