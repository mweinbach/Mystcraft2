/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import ports.mekanism.lib.client.extensions.IForgeKeyMapping;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IForgeKeyMapping {
}
