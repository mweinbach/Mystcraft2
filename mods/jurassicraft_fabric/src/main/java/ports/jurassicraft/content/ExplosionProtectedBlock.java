package ports.jurassicraft.content;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Explosions destroy these fragile deposits without producing their contents. */
public final class ExplosionProtectedBlock extends Block {
    public ExplosionProtectedBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    public boolean dropFromExplosion(Explosion explosion) { return false; }
}
