package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import ports.gridwright.block.GridMemberBlock;
import ports.gridwright.registry.ModPower;

import javax.annotation.Nullable;

/**
 * The power converter. Using it shows what it is doing; sneak-using it with an empty hand switches
 * between making energy from Grid Power and making Grid Power from energy.
 */
public class ConverterBlock extends GridMemberBlock {

    public static final EnumProperty<ConverterMode> MODE = EnumProperty.create("mode", ConverterMode.class);

    public ConverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, ConverterMode.TO_ENERGY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConverterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModPower.POWER_CONVERTER_ENTITY.get(), ConverterBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ConverterBlockEntity converter) {
            if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()) {
                level.setBlock(pos, state.setValue(MODE, state.getValue(MODE).next()), Block.UPDATE_ALL);
                converter.modeChanged();
            }
            player.displayClientMessage(converter.describe().copy().append(" | ").append(converter.gridStatus(server.getServer())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
