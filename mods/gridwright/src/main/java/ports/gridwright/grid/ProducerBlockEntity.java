package ports.gridwright.grid;

import ports.gridwright.block.ProducerBlock;
import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A panel or mill. It re-reads its surroundings once a second and reports its output to its owner's
 * grid every tick.
 */
public class ProducerBlockEntity extends GridBlockEntity {

    /** How often, in ticks, a producer re-reads its surroundings. */
    public static final int REFRESH_TICKS = 20;

    private double output;
    private int refresh;

    public ProducerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRODUCER.get(), pos, state);
    }

    public ProducerKind kind() {
        return ((ProducerBlock) getBlockState().getBlock()).kind();
    }

    /** The output measured at the last refresh, before diminishing returns. */
    public double output() {
        return output;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel server) || !(blockEntity instanceof ProducerBlockEntity producer)) return;
        if (producer.refresh-- <= 0) {
            producer.output = producer.kind().output(server, pos);
            producer.refresh = REFRESH_TICKS - 1;
        }
        String grid = producer.gridKey(server.getServer());
        if (grid != null) {
            GridManager.get(server.getServer()).addProduction(grid,
                    Registry.BLOCK.getKey(state.getBlock()).toString(), producer.output);
        }
    }
}
