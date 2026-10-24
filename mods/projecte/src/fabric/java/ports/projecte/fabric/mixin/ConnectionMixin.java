/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import ports.projecte.lib.common.extensions.IForgeConnection;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements IForgeConnection {
}
