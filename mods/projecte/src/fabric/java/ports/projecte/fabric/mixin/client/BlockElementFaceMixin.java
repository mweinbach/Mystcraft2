/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import net.minecraft.client.renderer.block.model.BlockElementFace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ports.projecte.fabric.client.EmissiveBlockElementFace;

/**
 * Holds the emissivity Forge adds to a model face. Vanilla's face has no field for it.
 */
@Mixin(BlockElementFace.class)
public abstract class BlockElementFaceMixin implements EmissiveBlockElementFace {

    @Unique
    private int projecte$emissivity;

    @Override
    public int projecte$emissivity() {
        return projecte$emissivity;
    }

    @Override
    public void projecte$setEmissivity(int emissivity) {
        this.projecte$emissivity = emissivity;
    }
}
