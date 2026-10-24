/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge's marker for blocks and items that plant something. On Fabric, vanilla's bushes, cactus and sugar cane
 * implement it through {@code PlantableBlocksMixin}, as Forge patches them to. The methods carry the port prefix so
 * they never meet another mod's same-named method on those vanilla classes.
 */
public interface IPlantable {

    default PlantType projecte$getPlantType(BlockGetter level, BlockPos pos) {
        return PlantType.PLAINS;
    }

    BlockState projecte$getPlant(BlockGetter level, BlockPos pos);
}
