package ports.jurassicraft.journal.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ports.jurassicraft.journal.JournalType;

@OnlyIn(Dist.CLIENT)
public final class JournalClient {
    private JournalClient() {}
    public static void open(JournalType type) { Minecraft.getInstance().setScreen(new JournalScreen(type)); }
}
