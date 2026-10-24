package ports.gridwright.block;

import ports.gridwright.platform.Platform;
import ports.gridwright.storage.TrashCanBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
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
 * Destroys items, fluids or energy. Item cans open a chest row whose slots destroy whatever is put in
 * them; fluid cans empty a held bucket or tank; energy cans only take energy from cables.
 */
public class TrashCanBlock extends BaseEntityBlock {

    private final TrashKind kind;

    public TrashCanBlock(TrashKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public TrashKind kind() {
        return kind;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrashCanBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        switch (kind) {
            case ITEM -> {
                if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrashCanBlockEntity can) {
                    player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, can, 1), getName()));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            case FLUID -> {
                if (held.isEmpty() || held.getItem() instanceof BlockItem) return InteractionResult.PASS;
                if (level.isClientSide) return InteractionResult.SUCCESS;
                return Platform.interactWithFluidStorage(player, hand, level, pos, hit.getDirection())
                        ? InteractionResult.CONSUME : InteractionResult.PASS;
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright." + kind.registryName()));
    }
}
