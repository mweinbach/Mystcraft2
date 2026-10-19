package ports.jurassicraft.journal;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;

public final class JournalRegistry {
    public static final ResourceLocation OPEN = new ResourceLocation(PortMod.MOD_ID, "journal_open");
    private static Item journal;
    public static final Supplier<Item> JOURNAL = () -> journal;
    private JournalRegistry() {}
    public static void init() {
        journal = Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, "ingen_journal"), new JournalItem());
    }
    static void open(ServerPlayer player, JournalType type) {
        var buffer = PacketByteBufs.create();
        buffer.writeUtf(type.id());
        ServerPlayNetworking.send(player, OPEN, buffer);
    }
}
