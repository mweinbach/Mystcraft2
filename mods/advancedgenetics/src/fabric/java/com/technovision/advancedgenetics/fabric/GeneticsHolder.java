package com.technovision.advancedgenetics.fabric;

import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;

/** Implemented on every player by {@code PlayerFabricMixin}, replacing the Cardinal Components player component. */
public interface GeneticsHolder {

    /** Top-level player save key. Upstream's Fabric build saved under {@code cardinal_components} with this id. */
    String SAVE_KEY = "advancedgenetics:player_genetics";

    PlayerGeneticsComponent advancedgenetics$genetics();
}
