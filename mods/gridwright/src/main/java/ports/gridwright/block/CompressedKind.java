package ports.gridwright.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

/**
 * Storage blocks for bulk junk: each tier is nine of the tier below, crafted back and forth, so tier
 * {@code n} holds {@code 9^n} of the base block. Pickaxe blocks get harder with every tier; the
 * shovel blocks stay quick to dig.
 */
public enum CompressedKind {
    COBBLESTONE(8, Material.STONE, SoundType.STONE, 2.0F, true),
    DIRT(4, Material.DIRT, SoundType.GRAVEL, 0.5F, false),
    GRAVEL(2, Material.SAND, SoundType.GRAVEL, 0.6F, false),
    SAND(2, Material.SAND, SoundType.SAND, 0.5F, false),
    NETHERRACK(6, Material.STONE, SoundType.NETHERRACK, 0.4F, true);

    /** Registry name prefixes for tiers 1 to 8. */
    public static final String[] PREFIXES = {"", "double_", "triple_", "quadruple_", "quintuple_", "sextuple_", "septuple_", "octuple_"};

    private final int tiers;
    private final Material material;
    private final SoundType sound;
    private final float hardness;
    private final boolean pickaxe;

    CompressedKind(int tiers, Material material, SoundType sound, float hardness, boolean pickaxe) {
        this.tiers = tiers;
        this.material = material;
        this.sound = sound;
        this.hardness = hardness;
        this.pickaxe = pickaxe;
    }

    public int tiers() {
        return tiers;
    }

    /** Mined with a pickaxe (and needing one to drop), rather than a shovel. */
    public boolean pickaxe() {
        return pickaxe;
    }

    /** {@code 9^tier}: how many base blocks one block of {@code tier} holds. */
    public static long contents(int tier) {
        long count = 1;
        for (int i = 0; i < tier; i++) count *= 9;
        return count;
    }

    public BlockBehaviour.Properties properties(int tier) {
        float strength = hardness + (pickaxe ? 1.5F : 0.25F) * tier;
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of(material).sound(sound).strength(strength, strength * 3);
        return pickaxe ? properties.requiresCorrectToolForDrops() : properties;
    }
}
