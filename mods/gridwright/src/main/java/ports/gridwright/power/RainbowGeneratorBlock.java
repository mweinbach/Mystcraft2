package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModPower;

import javax.annotation.Nullable;

/** The rainbow generator. Using it shows its output, which generator kinds it still lacks, and its grid. */
public class RainbowGeneratorBlock extends GridMemberBlock {

    public RainbowGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RainbowGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModPower.RAINBOW_GENERATOR_ENTITY.get(), RainbowGeneratorBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof RainbowGeneratorBlockEntity rainbow) {
            MutableComponent message = Component.translatable("message.gridwright.producer.output", GridBlockEntity.format(rainbow.output()));
            switch (rainbow.status()) {
                case CROWDED -> message.append(" | ").append(Component.translatable("message.gridwright.rainbow.crowded"));
                case MISSING -> {
                    MutableComponent kinds = Component.empty();
                    boolean first = true;
                    for (GeneratorKind kind : rainbow.missing()) {
                        if (!first) kinds.append(", ");
                        kinds.append(ModBlocks.GENERATORS.get(kind).get().getName());
                        first = false;
                    }
                    message.append(" | ").append(Component.translatable("message.gridwright.rainbow.missing", kinds));
                }
                case RUNNING -> { }
            }
            player.displayClientMessage(message.append(" | ").append(rainbow.gridStatus(server.getServer())), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
