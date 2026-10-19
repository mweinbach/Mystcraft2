package com.technovision.advancedgenetics.client;

import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;

public class ClientPacketHandler {

    public static void handleGeneticsSync(CompoundTag genetics) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ComponentRegistry.getGenetics(player).deserializeNBT(genetics);
        }
    }
}
