/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import ports.projecte.lib.common.extensions.IForgeEntityType;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin implements IForgeEntityType {
}
