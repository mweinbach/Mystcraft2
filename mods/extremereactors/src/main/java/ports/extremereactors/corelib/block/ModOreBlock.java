/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An ore that drops between minXP and maxXP experience when mined without Silk Touch.
 */
public class ModOreBlock extends ModBlock {

    public ModOreBlock(final Block.Properties properties) {
        this(properties, 0, 0);
    }

    public ModOreBlock(final Block.Properties properties, final int minDroppedXP, final int maxDroppedXP) {

        super(properties);
        this._xpRange = UniformInt.of(Math.min(minDroppedXP, maxDroppedXP), Math.max(minDroppedXP, maxDroppedXP));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void spawnAfterBreak(final BlockState state, final ServerLevel world, final BlockPos position,
                                final ItemStack tool, final boolean dropExperience) {

        super.spawnAfterBreak(state, world, position, tool, dropExperience);

        if (dropExperience && 0 == EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool)) {

            final int xp = this._xpRange.sample(world.random);

            if (xp > 0) {
                this.popExperience(world, position, xp);
            }
        }
    }

    private final UniformInt _xpRange;
}
