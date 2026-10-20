package ports.buildcraft.builders;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Upstream {@code BlockElectronicLibrary}: faces the player who placed it and opens its GUI when used. */
public class ElectronicLibraryBlock extends FacingMachineBlock {
    public ElectronicLibraryBlock(Properties properties) {
        super(properties);
    }

    /** Upstream {@code ItemBlockBC_Neptune} showed {@code tile.libraryBlock.tip} under the name. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.buildcraft.electronic_library.tip").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectronicLibraryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BCBuilders.ELECTRONIC_LIBRARY_TYPE.get(),
                level.isClientSide ? (l, p, s, library) -> library.clientTick() : (l, p, s, library) -> library.serverTick());
    }
}
