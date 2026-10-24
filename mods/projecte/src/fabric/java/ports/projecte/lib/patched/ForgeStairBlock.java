/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import java.util.function.Supplier;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge adds a {@link Supplier} constructor to {@link StairBlock} so a stair can name its base block before that
 * block's registry object is created. The supplier is resolved when the stair is constructed, which is during block
 * registration, after the base block in the same register.
 */
public class ForgeStairBlock extends StairBlock {

    public ForgeStairBlock(Supplier<BlockState> state, BlockBehaviour.Properties properties) {
        super(state.get(), properties);
    }
}
