package ports.gridwright.automation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ports.gridwright.block.GridMemberBlock;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.item.GoldenLassoItem;
import ports.gridwright.registry.ModAutomation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The chunk-loading ward; see {@link ChunkWardBlockEntity}. Use it with a lasso carrying a villager to
 * sign the contract (the villager is used up), or empty-handed to see what it is doing. The contract
 * is kept in the dropped item.
 */
public class ChunkWardBlock extends GridMemberBlock {

    public ChunkWardBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkWardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModAutomation.CHUNK_WARD_ENTITY.get(), ChunkWardBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChunkWardBlockEntity ward)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof GoldenLassoItem && GoldenLassoItem.holdsMob(held)) {
            if (!level.isClientSide) {
                boolean villager = GoldenLassoItem.heldType(held).filter(type -> type == EntityType.VILLAGER).isPresent();
                if (!villager) {
                    player.displayClientMessage(Component.translatable("message.gridwright.chunk_ward.needs_villager"), true);
                } else if (ward.contract()) {
                    GoldenLassoItem.empty(held);
                    level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.gridwright.chunk_ward.contracted"), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.gridwright.chunk_ward.already"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            Component message = switch (ward.status()) {
                case LOADING -> Component.translatable("message.gridwright.chunk_ward.loading", GridBlockEntity.format(ChunkWardBlockEntity.GP));
                case UNPOWERED -> Component.translatable(ward.loading(server) ? "message.gridwright.chunk_ward.unpaid"
                        : "message.gridwright.chunk_ward.unpowered", GridBlockEntity.format(ChunkWardBlockEntity.GP));
                case NO_OWNER -> Component.translatable("message.gridwright.ward.no_owner");
                case NO_CONTRACT -> Component.translatable("message.gridwright.chunk_ward.no_contract");
                case LIMIT -> Component.translatable("message.gridwright.chunk_ward.limit", ChunkWards.limit(server.getServer()));
            };
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof ChunkWardBlockEntity ward) {
            ward.release(server);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.chunk_ward", GridBlockEntity.format(ChunkWardBlockEntity.GP))
                .withStyle(ChatFormatting.GRAY));
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        tooltip.add(data != null && data.getBoolean("Contracted")
                ? Component.translatable("tooltip.gridwright.chunk_ward.contracted").withStyle(ChatFormatting.GOLD)
                : Component.translatable("tooltip.gridwright.chunk_ward.no_contract").withStyle(ChatFormatting.GRAY));
    }
}
