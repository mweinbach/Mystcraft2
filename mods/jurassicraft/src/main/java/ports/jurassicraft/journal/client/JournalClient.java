package ports.jurassicraft.journal.client;

import net.minecraft.client.Minecraft;
import ports.jurassicraft.journal.JournalItem;
import ports.jurassicraft.journal.JournalType;

public final class JournalClient {
    private JournalClient() {}
    public static void setup() { JournalItem.setOpener(JournalClient::open); }
    public static void open(JournalType type) { Minecraft.getInstance().setScreen(new JournalScreen(type)); }
}
