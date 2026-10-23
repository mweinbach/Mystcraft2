/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.IForgeShearable;

/**
 * Forge's mooshrooms implement {@link IForgeShearable}. Vanilla {@link MushroomCow#shear} still drops the mushrooms.
 */
@Mixin(MushroomCow.class)
public abstract class MushroomCowMixin implements IForgeShearable {

    @Override
    public boolean mekanism$isShearable(ItemStack item, Level level, BlockPos pos) {
        MushroomCow cow = (MushroomCow) (Object) this;
        return cow.isAlive() && !cow.isBaby();
    }

    @Override
    public List<ItemStack> mekanism$onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos, int fortune) {
        MushroomCow cow = (MushroomCow) (Object) this;
        cow.shear(player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS);
        cow.gameEvent(GameEvent.SHEAR, player);
        return List.of();
    }
}
