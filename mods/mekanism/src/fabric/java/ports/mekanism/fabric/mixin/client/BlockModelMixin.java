/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.renderer.block.model.BlockModel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ports.mekanism.lib.client.extensions.IForgeBlockModel;
import ports.mekanism.lib.client.model.geometry.BlockGeometryBakingContext;

/**
 * Gives every block model the Forge geometry context Forge keeps in {@code BlockModel.customData}.
 */
@Mixin(BlockModel.class)
public abstract class BlockModelMixin implements IForgeBlockModel {

    @Unique
    @Nullable
    private BlockGeometryBakingContext mekanism$customData;

    @Override
    public BlockGeometryBakingContext getCustomData() {
        if (mekanism$customData == null) {
            mekanism$customData = new BlockGeometryBakingContext((BlockModel) (Object) this);
        }
        return mekanism$customData;
    }
}
