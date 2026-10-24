/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import ports.projecte.lib.common.extensions.IForgeMobEffectInstance;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin implements IForgeMobEffectInstance {
}
