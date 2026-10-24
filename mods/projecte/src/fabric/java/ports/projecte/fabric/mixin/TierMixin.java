/*
 * ProjectE 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.item.Tier;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeTier;

/**
 * Makes every {@link Tier} an {@link IForgeTier} at runtime, as the injected interface already does at compile time, so
 * {@code TierSortingRegistry} can read a registered tier's {@code projecte$getTag}.
 */
@Mixin(Tier.class)
public interface TierMixin extends IForgeTier {
}
