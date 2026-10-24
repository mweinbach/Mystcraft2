package ports.gridwright.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

/** Drum sizes, in buckets. A drum holds one fluid and keeps it when broken. */
public enum DrumTier {
    STONE(16, Material.STONE, SoundType.STONE, 2.0F),
    IRON(256, Material.METAL, SoundType.METAL, 4.0F),
    REINFORCED(4_096, Material.METAL, SoundType.METAL, 6.0F),
    GARGANTUAN(65_536, Material.METAL, SoundType.NETHERITE_BLOCK, 10.0F);

    private final int buckets;
    private final Material material;
    private final SoundType sound;
    private final float hardness;

    DrumTier(int buckets, Material material, SoundType sound, float hardness) {
        this.buckets = buckets;
        this.material = material;
        this.sound = sound;
        this.hardness = hardness;
    }

    public int buckets() {
        return buckets;
    }

    public BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of(material).sound(sound).strength(hardness, hardness * 3).requiresCorrectToolForDrops();
    }
}
