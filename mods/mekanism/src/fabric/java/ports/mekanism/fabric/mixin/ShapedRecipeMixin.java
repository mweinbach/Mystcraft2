/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import ports.mekanism.lib.common.extensions.IForgeShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin implements IForgeShapedRecipe {
}
