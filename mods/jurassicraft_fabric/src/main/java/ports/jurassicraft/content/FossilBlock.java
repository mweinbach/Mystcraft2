package ports.jurassicraft.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.Explosion;

/** A deposit retains its species until it is encased and cleaned. */
public final class FossilBlock extends Block {
    private final Species species;

    public FossilBlock(Species species) {
        super(BlockBehaviour.Properties.of(Material.STONE).strength(2.0F, 8.0F)
                .sound(SoundType.STONE).requiresCorrectToolForDrops().noLootTable());
        this.species = species;
    }

    public Species species() { return species; }

    @Override
    public boolean dropFromExplosion(Explosion explosion) { return false; }
}
