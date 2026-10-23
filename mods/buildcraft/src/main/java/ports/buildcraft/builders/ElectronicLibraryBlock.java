package ports.buildcraft.builders;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
