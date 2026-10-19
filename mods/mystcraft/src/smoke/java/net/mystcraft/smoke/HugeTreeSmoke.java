package net.mystcraft.smoke;

import net.mystcraft.minecraft.age.generation.*;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

/** Test-only actual ProtoChunk placements against independently extracted source block-kind goldens. */
public final class HugeTreeSmoke {
    private HugeTreeSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) throws Exception {
        check(server.isSameThread(), "Huge Tree smoke must run on server thread");
        String[] expected = {
            "d7c1c9504660f4051e515242e169d944aad5480e869fee5dbe7c52844d44ef3b",
            "da24b65f02293903a1699afb48005929345e23a9090959b4b452ad6835b6ea09"
        };
        for (int variant = 0; variant < 2; variant++) {
            ChunkAccess chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
                    LevelHeightAccessor.create(0, 256), server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), null);
            if (variant == 1) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 64; y++)
                chunk.setBlockState(new BlockPos(x, y, z), (y == 0 ? Blocks.BEDROCK : Blocks.STONE).defaultBlockState(), false);
            new ClassicHugeTrees(73).generate(chunk);
            byte[] kinds = new byte[65536]; int logs = 0, leaves = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 256; y++) {
                var state = chunk.getBlockState(new BlockPos(x, y, z));
                int kind = state.isAir() ? 0 : state.is(Blocks.STONE) ? 1 : state.is(Blocks.BEDROCK) ? 2
                        : state.is(Blocks.OAK_LOG) ? 3 : state.is(Blocks.OAK_LEAVES) ? 4 : -1;
                check(kind >= 0, "Unexpected Huge Tree block");
                kinds[(x * 16 + z) * 256 + y] = (byte) kind;
                if (kind == 3) logs++;
                if (kind == 4) {
                    leaves++;
                    check(state.getValue(LeavesBlock.DISTANCE) == 1 && !state.getValue(LeavesBlock.PERSISTENT), "Modern leaves must start viable but remain natural");
                }
                if (variant == 1 && y == 0) check(kind == 2, "Huge Trees replaced bedrock");
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(kinds));
            check(hash.equals(expected[variant]), "Huge Tree source geometry differs: variant=" + variant + " hash=" + hash);
            check(logs == (variant == 0 ? 154 : 149) && leaves == 1061, "Huge Tree source counts changed");
        }
        return List.of("Huge Trees: actual ProtoChunk placement matches source goldens on air and stone/bedrock, with natural viable leaf states");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
