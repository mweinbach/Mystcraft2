package ports.jurassicraft.structures;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ports.jurassicraft.config.JurassicConfig;

/**
 * One independent roll per chunk, matching {@code random.nextInt(paddockRarity) == 0}.
 * Spacing stays 1 so vanilla locate still walks chunks; it does not thin the attempts.
 */
public final class PaddockPlacement extends RandomSpreadStructurePlacement {
    public static final Codec<PaddockPlacement> CODEC = Codec.unit(PaddockPlacement::new);
    private static final int SALT = 185379823;
    private final int rarity;

    public PaddockPlacement() { this(JurassicConfig.paddockRarity()); }

    PaddockPlacement(int rarity) {
        super(1, 0, RandomSpreadType.LINEAR, SALT);
        this.rarity = Math.max(1, rarity);
    }

    public int rarity() { return rarity; }

    @Override
    protected boolean isPlacementChunk(ChunkGenerator generator, RandomState state, long seed, int chunkX, int chunkZ) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, chunkX, chunkZ, SALT);
        // Upstream picked a block inside the chunk, then the only biome generator, then the rarity.
        random.nextInt(16);
        random.nextInt(16);
        random.nextInt(1);
        return rarity <= 1 || random.nextInt(rarity) == 0;
    }

    @Override public StructurePlacementType<?> type() { return StructureRegistry.PADDOCK_PLACEMENT.get(); }
}
