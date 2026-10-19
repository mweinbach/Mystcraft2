package ports.jurassicraft.fieldguide.client;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.fieldguide.FieldGuideNetwork;

public final class FieldGuideClient {
    private FieldGuideClient() {}
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(FieldGuideNetwork.MANUAL, (client, handler, buffer, sender) ->
                client.execute(FieldGuideClient::openManual));
        ClientPlayNetworking.registerGlobalReceiver(FieldGuideNetwork.SNAPSHOT, (client, handler, buffer, sender) -> {
            FieldGuideNetwork.Snapshot snapshot = FieldGuideNetwork.Snapshot.read(buffer);
            client.execute(() -> open(snapshot));
        });
    }
    public static void openManual() { Minecraft.getInstance().setScreen(new FieldGuideScreen(null, null)); }
    public static void open(FieldGuideNetwork.Snapshot snapshot) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(snapshot.entityId()) instanceof DinosaurEntity dinosaur)
            minecraft.setScreen(new FieldGuideScreen(dinosaur, snapshot));
    }
}
