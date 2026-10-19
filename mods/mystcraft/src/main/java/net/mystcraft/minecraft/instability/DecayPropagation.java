package net.mystcraft.minecraft.instability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.mystcraft.minecraft.age.AgeLevelData;

/** Classic per-neighbor conversion odds, adapted to bounded loaded-neighbor scheduled pulses. */
public final class DecayPropagation {
    /** Port scheduler cadence, not a reconstructed classic instability/ExtraTicks frequency. */
    public static final int PULSE_INTERVAL = 20;
    // UPDATE_CLIENTS alone still invokes neighbor shape updates and can request border chunks.
    public static final int CONVERSION_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private DecayPropagation() {}
    public static boolean isAge(ServerLevel level) { return level.getLevelData() instanceof AgeLevelData; }
    public static int difficulty(DecayType type, BlockState target, ServerLevel level, BlockPos position) {
        return difficulty(type, target.isAir(), target.getBlock() instanceof DecayBlock,
                target.getMaterial().isLiquid(), target.getDestroySpeed(level, position), target.getBlock().getExplosionResistance());
    }
    /** Exact arithmetic order matters: classic blue truncates hardness before multiplying by two. */
    public static int difficulty(DecayType type, boolean air, boolean decay, boolean liquid, float hardness, float resistance) {
        return switch (type) {
            case BLACK -> throw new IllegalArgumentException("Black decay uses corruption and falling, not spreading odds");
            case RED -> air ? 20 : Math.max(1, (int) clamp(resistance));
            case BLUE -> air ? 20 : Math.max(1, (int) clamp(hardness) * 2);
            case PURPLE -> air ? 8 : decay ? 5 : liquid ? 3 : Math.max(1, (int) (clamp(hardness) * 2 + clamp(resistance))) * 10;
            case WHITE -> air ? 50 : 1;
        };
    }
    private static float clamp(float value) { return value < 0 || value > 1000 ? 1000 : value; }
    /** At most six conversions. Never requests an unloaded chunk or writes outside build height. */
    public static int pulse(ServerLevel level, BlockPos source, DecayBlock block, RandomSource random) {
        if (!isAge(level) || !level.hasChunkAt(source) || !level.getBlockState(source).is(block)) return 0;
        if(block.type()==DecayType.BLACK) return BlackDecay.pulse(level,source,random);
        int converted = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = source.relative(direction);
            if (level.isOutsideBuildHeight(neighbor) || !level.hasChunkAt(neighbor)) continue;
            BlockState target = level.getBlockState(neighbor);
            if (target.is(block)) continue;
            if (random.nextInt(difficulty(block.type(), target, level, neighbor)) == 0
                    && level.setBlock(neighbor, block.defaultBlockState(), CONVERSION_FLAGS)) converted++;
        }
        return converted;
    }
}
