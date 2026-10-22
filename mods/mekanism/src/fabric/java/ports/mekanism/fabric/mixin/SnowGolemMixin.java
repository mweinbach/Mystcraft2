/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.IForgeShearable;

/**
 * Forge's snow golems implement {@link IForgeShearable}. Vanilla {@link SnowGolem#shear} still drops the pumpkin.
 */
@Mixin(SnowGolem.class)
public abstract class SnowGolemMixin implements IForgeShearable {

    @Override
    public boolean isShearable(ItemStack item, Level level, BlockPos pos) {
        SnowGolem golem = (SnowGolem) (Object) this;
        return golem.isAlive() && golem.hasPumpkin();
    }

    @Override
    public List<ItemStack> onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos, int fortune) {
        SnowGolem golem = (SnowGolem) (Object) this;
        golem.shear(player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS);
        golem.gameEvent(GameEvent.SHEAR, player);
        return List.of();
    }
}
