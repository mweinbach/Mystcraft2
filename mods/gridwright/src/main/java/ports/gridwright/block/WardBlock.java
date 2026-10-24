package ports.gridwright.block;

import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.registry.ModUtilities;
import ports.gridwright.storage.WardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/** A ward; see {@link WardKind} for what each one stops and {@link ports.gridwright.storage.Wards} for how. */
public class WardBlock extends GridMemberBlock {

    private final WardKind kind;

    public WardBlock(WardKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public WardKind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || !kind.powered() ? null
                : createTickerHelper(type, ModUtilities.WARD.get(), WardBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WardBlockEntity ward)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            Component message = switch (ward.status()) {
                case ACTIVE -> Component.translatable("message.gridwright.ward.active", kind.radius());
                case UNPOWERED -> Component.translatable("message.gridwright.ward.unpowered", GridBlockEntity.format(kind.gp()));
                case NO_OWNER -> Component.translatable("message.gridwright.ward.no_owner");
            };
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        String effect = kind.effect() == WardKind.Effect.SPAWNS ? "spawns" : "teleports";
        tooltip.add(Component.translatable("tooltip.gridwright.ward." + effect, kind.radius()));
        if (kind.powered()) tooltip.add(Component.translatable("tooltip.gridwright.ward.gp", GridBlockEntity.format(kind.gp())));
    }
}
