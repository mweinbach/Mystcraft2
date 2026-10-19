package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Player genetics, stored by each loader under {@link #PLAYER_GENETICS_ID}. This replaces the
 * Cardinal Components player component upstream used.
 */
public class ComponentRegistry {

    public static final ResourceLocation PLAYER_GENETICS_ID = new ResourceLocation(AdvancedGenetics.MOD_ID, "player_genetics");

    public static PlayerGeneticsComponent getGenetics(Player player) {
        return Platform.genetics(player);
    }
}
