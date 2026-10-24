package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.registry.ModPower;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The rainbow generator: a Grid Power producer that gives {@link #OUTPUT} GP while one generator of
 * every Gridwright kind is running within {@link #RANGE} blocks. "Running" means the generator turned
 * fuel into energy in the last second, so a full generator that is only holding its flame does not
 * count. Two rainbow generators whose ranges overlap both stop, so one set of generators can never
 * feed more than one of them.
 */
public class RainbowGeneratorBlockEntity extends GridBlockEntity {

    public static final double OUTPUT = 128.0;
    /** Half the side of the cube searched for generators, centred on the rainbow generator. */
    public static final int RANGE = 4;
    public static final int REFRESH_TICKS = 20;

    /** What the last refresh found. */
    public enum Status { RUNNING, MISSING, CROWDED }

    private double output;
    private int refresh;
    private Status status = Status.MISSING;
    private final EnumSet<GeneratorKind> missing = EnumSet.allOf(GeneratorKind.class);

    public RainbowGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModPower.RAINBOW_GENERATOR_ENTITY.get(), pos, state);
    }

    public double output() {
        return output;
    }

    public Status status() {
        return status;
    }

    /** Generator kinds with no running generator in range at the last refresh. */
    public Set<GeneratorKind> missing() {
        return EnumSet.copyOf(missing);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel server) || !(blockEntity instanceof RainbowGeneratorBlockEntity rainbow)) return;
        if (rainbow.refresh-- <= 0) {
            rainbow.survey(server, pos);
            rainbow.refresh = REFRESH_TICKS - 1;
        }
        String grid = rainbow.gridKey(server.getServer());
        if (grid != null) {
            GridManager.get(server.getServer()).addProduction(grid, Registry.BLOCK.getKey(state.getBlock()).toString(), rainbow.output);
        }
    }

    private void survey(ServerLevel level, BlockPos pos) {
        missing.clear();
        missing.addAll(EnumSet.allOf(GeneratorKind.class));
        boolean crowded = false;
        long now = level.getGameTime();
        // Generators count within RANGE; another rainbow generator within 2 * RANGE would share them.
        for (BlockEntity other : blockEntitiesNear(level, pos, 2 * RANGE)) {
            BlockPos at = other.getBlockPos();
            if (other instanceof RainbowGeneratorBlockEntity && other != this) crowded = true;
            if (other instanceof GeneratorBlockEntity generator && within(pos, at, RANGE) && generator.running(now)) {
                missing.remove(generator.kind());
            }
        }
        status = crowded ? Status.CROWDED : missing.isEmpty() ? Status.RUNNING : Status.MISSING;
        output = status == Status.RUNNING ? OUTPUT : 0;
    }

    private static boolean within(BlockPos centre, BlockPos at, int range) {
        return Math.abs(at.getX() - centre.getX()) <= range && Math.abs(at.getY() - centre.getY()) <= range
                && Math.abs(at.getZ() - centre.getZ()) <= range;
    }

    /** Loaded block entities in the cube of half-side {@code range} around {@code pos}, read from the chunks. */
    private static List<BlockEntity> blockEntitiesNear(ServerLevel level, BlockPos pos, int range) {
        AABB box = new AABB(pos).inflate(range);
        List<BlockEntity> found = new ArrayList<>();
        for (int cx = (pos.getX() - range) >> 4; cx <= (pos.getX() + range) >> 4; cx++) {
            for (int cz = (pos.getZ() - range) >> 4; cz <= (pos.getZ() + range) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos at = blockEntity.getBlockPos();
                    if (!blockEntity.isRemoved() && box.contains(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)) {
                        found.add(blockEntity);
                    }
                }
            }
        }
        return found;
    }
}
