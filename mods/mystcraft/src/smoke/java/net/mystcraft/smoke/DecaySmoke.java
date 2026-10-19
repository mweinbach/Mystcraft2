package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.DecayRegistry;
import net.mystcraft.minecraft.instability.DecayBlock;
import net.mystcraft.minecraft.instability.DecayPropagation;
import net.mystcraft.minecraft.instability.DecayType;

/** Real Age-only hazard checks; run after AgeSmoke creates a genuine persistent Age. */
public final class DecaySmoke {
    private DecaySmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel origin) {
        List<String> passed = new ArrayList<>();
        formulas();
        passed.add("classic decay: source red/blue/purple/white air, liquid, other-decay, fractional hardness and resistance-clamp probabilities match handler arithmetic");
        ServerLevel age = null;
        for (ServerLevel candidate : server.getAllLevels()) if (DecayPropagation.isAge(candidate)) { age = candidate; break; }
        require(age != null, "Run AgeSmoke before DecaySmoke: genuine Age level required");
        ServerLevel overworld = server.overworld();
        BlockPos outside = new BlockPos(3208, 200, 3208), source = new BlockPos(3208, 100, 3208);
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> entities = new LinkedHashMap<>();
        BlockState oldOutside = overworld.getBlockState(outside);
        BlockEntity outsideEntity = overworld.getBlockEntity(outside);
        CompoundTag oldOutsideData = outsideEntity == null ? null : outsideEntity.saveWithFullMetadata();
        try {
            for (BlockPos pos : BlockPos.betweenClosed(source.offset(-2, -2, -2), source.offset(2, 2, 2))) remember(age, pos.immutable(), states, entities);
            for (BlockPos pos : states.keySet()) age.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            for (DecayType type : DecayType.values()) {
                Block block = DecayRegistry.block(type).get();
                overworld.setBlock(outside, block.defaultBlockState(), 2);
                require(overworld.getBlockState(outside).isAir(), "Decay survived placement outside a Mystcraft Age: " + type);
                age.setBlock(source, block.defaultBlockState(), 2);
                require(age.getBlockState(source).is(block), "Explicit decay did not survive in genuine Age: " + type);
                require(age.getBlockTicks().hasScheduledTick(source, block), "Placed decay has no server scheduled pulse: " + type);
                require(block.defaultBlockState().getDestroySpeed(age, source) == type.hardness() && block.getExplosionResistance() == type.resistance(), "Classic decay physical properties differ: " + type);
                require(Block.getDrops(block.defaultBlockState(), age, source, null).isEmpty(), "Decay unexpectedly drops an item when broken: " + type);
            }
            passed.add("classic decay: four registered original-art hazards self-remove outside Ages, schedule only when explicitly placed in a real Age, retain classic hardness/resistance and drop nothing");

            DecayBlock white = (DecayBlock) DecayRegistry.block(DecayType.WHITE).get();
            for (Direction direction : Direction.values()) age.setBlock(source.relative(direction), Blocks.STONE.defaultBlockState(), 2);
            require(DecayPropagation.pulse(age, source, white, RandomSource.create(9183)) == 6, "White pulse did not convert exactly six solid neighbors");
            for (Direction direction : Direction.values()) require(age.getBlockState(source.relative(direction)).is(white), "White pulse missed adjacent solid");
            require(age.getBlockState(source.offset(1, 1, 0)).isAir(), "Single pulse propagated diagonally or recursively");
            require(DecayPropagation.pulse(age, source, white, RandomSource.create(3)) == 0, "Same decay type was converted again");
            LivingEntity pig = EntityType.PIG.create(age);
            require(pig != null, "Could not create vanilla contact-test entity");
            pig.setPos(source.getX() + .5, source.getY() + 1, source.getZ() + .5);
            float health = pig.getHealth();
            ((Block) white).stepOn(age, source, ((Block) white).defaultBlockState(), pig);
            require(pig.getHealth() == health - 1, "White contact did not cause exactly one magic damage");
            passed.add("classic decay: real server pulse converts only six adjacent cells, skips its own color, and white contact applies exactly one magic damage");

            BlockPos edge = new BlockPos(3215, 100, 3208), unloaded = edge.east();
            // This optional assertion is meaningful only while the adjacent chunk was not already
            // loaded by another fixture. Never unload a chunk on the user's behalf to force it.
            if (!age.hasChunkAt(unloaded)) {
                remember(age, edge, states, entities);
                for (Direction direction : Direction.values()) if (direction != Direction.EAST)
                    remember(age, edge.relative(direction), states, entities);
                age.setBlock(edge, ((Block) white).defaultBlockState(), DecayPropagation.CONVERSION_FLAGS);
                for (Direction direction : Direction.values()) if (direction != Direction.EAST)
                    age.setBlock(edge.relative(direction), Blocks.STONE.defaultBlockState(), DecayPropagation.CONVERSION_FLAGS);
                require(!age.hasChunkAt(unloaded), "Boundary fixture setup loaded the sixth neighbor before propagation");
                require(DecayPropagation.pulse(age, edge, white, RandomSource.create(8)) == 5, "Chunk-edge pulse did not skip unloaded sixth neighbor");
                require(!age.hasChunkAt(unloaded), "Propagation forced an unloaded neighbor chunk");
                passed.add("classic decay: real loaded-chunk edge pulse converts five existing neighbors and does not load the sixth neighbor chunk");
            }
        } finally {
            for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) age.setBlock(entry.getKey(), entry.getValue(), 2);
            for (Map.Entry<BlockPos, CompoundTag> entry : entities.entrySet()) {
                BlockEntity restored = age.getBlockEntity(entry.getKey());
                if (restored != null) restored.load(entry.getValue());
            }
            overworld.setBlock(outside, oldOutside, 2);
            if (oldOutsideData != null && overworld.getBlockEntity(outside) != null) overworld.getBlockEntity(outside).load(oldOutsideData);
        }
        return passed;
    }
    private static void formulas() {
        require(d(DecayType.RED, true, false, false, 0, 0) == 20 && d(DecayType.RED, false, false, false, 5, 6.9F) == 6, "Red resistance/air arithmetic changed");
        require(d(DecayType.RED, false, false, false, 0, -1) == 1000, "Negative resistance clamp changed");
        require(d(DecayType.BLUE, true, false, false, 0, 0) == 20 && d(DecayType.BLUE, false, false, false, .9F, 4) == 1
                && d(DecayType.BLUE, false, false, false, 1.9F, 4) == 2 && d(DecayType.BLUE, false, false, false, -1, 4) == 2000, "Blue cast-before-multiply arithmetic changed");
        require(d(DecayType.PURPLE, true, false, false, 0, 0) == 8 && d(DecayType.PURPLE, false, true, false, 0, 0) == 5
                && d(DecayType.PURPLE, false, false, true, 0, 0) == 3 && d(DecayType.PURPLE, false, false, false, 2.5F, 6) == 110
                && d(DecayType.PURPLE, false, false, false, -1, -1) == 30000, "Purple air/decay/liquid/combined arithmetic changed");
        require(d(DecayType.WHITE, true, false, false, 0, 0) == 50 && d(DecayType.WHITE, false, false, false, -1, 99999) == 1, "White odds changed");
    }
    private static int d(DecayType type, boolean air, boolean decay, boolean liquid, float hardness, float resistance) {
        return DecayPropagation.difficulty(type, air, decay, liquid, hardness, resistance);
    }
    private static void remember(ServerLevel level, BlockPos pos, Map<BlockPos, BlockState> states, Map<BlockPos, CompoundTag> entities) {
        states.putIfAbsent(pos, level.getBlockState(pos));
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) entities.putIfAbsent(pos, entity.saveWithFullMetadata());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
