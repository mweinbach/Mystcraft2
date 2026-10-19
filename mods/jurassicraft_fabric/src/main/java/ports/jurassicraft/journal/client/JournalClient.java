package ports.jurassicraft.journal.client;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ports.jurassicraft.journal.JournalRegistry;
import ports.jurassicraft.journal.JournalType;

public final class JournalClient {
    private JournalClient() {}
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(JournalRegistry.OPEN, (client, handler, buffer, sender) -> {
            JournalType type = JournalType.byId(buffer.readUtf(64));
            client.execute(() -> open(type));
        });
    }
    public static void open(JournalType type) { Minecraft.getInstance().setScreen(new JournalScreen(type)); }
}
