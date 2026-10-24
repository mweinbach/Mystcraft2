/*
 * ProjectE 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.IPlantable;

/**
 * Forge makes vanilla's bushes (crops, saplings, flowers, mushrooms, stems, nether wart...), cactus and sugar cane
 * plantable; the Harvest Goddess band plants them from the player's inventory.
 */
@Mixin({BushBlock.class, CactusBlock.class, SugarCaneBlock.class})
public abstract class PlantableBlocksMixin implements IPlantable {

    @Override
    public BlockState projecte$getPlant(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block self = (Block) (Object) this;
        return state.getBlock() == self ? state : self.defaultBlockState();
    }
}
