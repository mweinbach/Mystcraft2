/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeExplosion;

/**
 * Gives explosions Forge's {@code getExploder}, which the Nova explosion reads. ProjectE's own explosion drives its
 * block hooks itself, so vanilla's explosion is not patched.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin implements IForgeExplosion {
}
