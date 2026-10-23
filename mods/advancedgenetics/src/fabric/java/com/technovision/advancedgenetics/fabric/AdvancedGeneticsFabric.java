package com.technovision.advancedgenetics.fabric;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.ModNetwork;
import com.technovision.advancedgenetics.platform.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class AdvancedGeneticsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        Path config = FabricLoader.getInstance().getConfigDir().resolve(Config.FILE_NAME);
        FabricConfig.load(config);
        FabricConfig.watch(config);
        AdvancedGenetics.init();
        platform.flush();
        FabricTransfer.register();
        FabricEvents.register();
        ServerPlayNetworking.registerGlobalReceiver(ModNetwork.KEY_PRESSED, (server, player, handler, buf, sender) -> {
            KeyPressedPacket packet = KeyPressedPacket.decode(buf);
            server.execute(() -> KeyPressedPacket.handle(packet, player));
        });
    }
}
