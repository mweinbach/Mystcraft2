package ports.jurassicraft.fieldguide.client;

import net.minecraft.client.Minecraft;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.fieldguide.FieldGuideItem;
import ports.jurassicraft.fieldguide.FieldGuideNetwork;
import ports.jurassicraft.platform.Network;

public final class FieldGuideClient {
    private FieldGuideClient() {}
    public static void setup() {
        FieldGuideItem.setManualOpener(FieldGuideClient::openManual);
        Network.registerClient(FieldGuideNetwork.SNAPSHOT, buffer -> open(FieldGuideNetwork.Snapshot.read(buffer)));
    }
    public static void openManual() { Minecraft.getInstance().setScreen(new FieldGuideScreen(null, null)); }
    public static void open(FieldGuideNetwork.Snapshot snapshot) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(snapshot.entityId()) instanceof DinosaurEntity dinosaur)
            minecraft.setScreen(new FieldGuideScreen(dinosaur, snapshot));
    }
}
