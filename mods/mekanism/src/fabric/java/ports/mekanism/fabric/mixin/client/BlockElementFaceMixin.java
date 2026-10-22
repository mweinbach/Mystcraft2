/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.renderer.block.model.BlockElementFace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ports.mekanism.fabric.client.EmissiveBlockElementFace;

/**
 * Holds the emissivity Forge adds to a model face. Vanilla's face has no field for it.
 */
@Mixin(BlockElementFace.class)
public abstract class BlockElementFaceMixin implements EmissiveBlockElementFace {

    @Unique
    private int mekanism$emissivity;

    @Override
    public int mekanism$emissivity() {
        return mekanism$emissivity;
    }

    @Override
    public void mekanism$setEmissivity(int emissivity) {
        this.mekanism$emissivity = emissivity;
    }
}
