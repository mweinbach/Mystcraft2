/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.level.block.LiquidBlock;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.extensions.IForgeLiquidBlock;

@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin implements IForgeLiquidBlock {
}
