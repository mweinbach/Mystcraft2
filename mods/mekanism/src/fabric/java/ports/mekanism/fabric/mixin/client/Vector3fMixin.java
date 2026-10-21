/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import ports.mekanism.lib.client.extensions.IForgeVector3f;
import com.mojang.math.Vector3f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Vector3f.class)
public abstract class Vector3fMixin implements IForgeVector3f {
}
