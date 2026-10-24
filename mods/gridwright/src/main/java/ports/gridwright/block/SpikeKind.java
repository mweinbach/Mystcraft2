package ports.gridwright.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

/**
 * Spike tiers. Wooden spikes never take a creature's last health point; iron spikes kill; golden and
 * diamond spikes kill as a player would, so creatures they kill drop experience and player-only loot.
 */
public enum SpikeKind {
    WOODEN(1.0F, false, false, Material.WOOD, SoundType.WOOD, 1.5F),
    IRON(4.0F, true, false, Material.METAL, SoundType.METAL, 4.0F),
    GOLDEN(2.0F, true, true, Material.METAL, SoundType.METAL, 3.0F),
    DIAMOND(7.0F, true, true, Material.METAL, SoundType.METAL, 5.0F);

    private final float damage;
    private final boolean lethal;
    private final boolean playerKill;
    private final Material material;
    private final SoundType sound;
    private final float hardness;

    SpikeKind(float damage, boolean lethal, boolean playerKill, Material material, SoundType sound, float hardness) {
        this.damage = damage;
        this.lethal = lethal;
        this.playerKill = playerKill;
        this.material = material;
        this.sound = sound;
        this.hardness = hardness;
    }

    /** Damage per hit; a creature's invulnerability after a hit limits it to one hit every ten ticks. */
    public float damage() {
        return damage;
    }

    /** Whether the spike may take a creature's last health point. */
    public boolean lethal() {
        return lethal;
    }

    /** Whether kills count as a player's, dropping experience and player-only loot. */
    public boolean playerKill() {
        return playerKill;
    }

    public BlockBehaviour.Properties properties() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of(material).sound(sound).strength(hardness, hardness * 2).noOcclusion();
        return this == WOODEN ? properties : properties.requiresCorrectToolForDrops();
    }
}
