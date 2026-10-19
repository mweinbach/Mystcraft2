package ports.jurassicraft.fieldguide.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.fieldguide.FieldGuideNetwork;

@OnlyIn(Dist.CLIENT)
public final class FieldGuideClient {
    private FieldGuideClient() {}
    public static void openManual() { Minecraft.getInstance().setScreen(new FieldGuideScreen(null, null)); }
    public static void open(FieldGuideNetwork.Snapshot snapshot) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(snapshot.entityId()) instanceof DinosaurEntity dinosaur)
            minecraft.setScreen(new FieldGuideScreen(dinosaur, snapshot));
    }
}
