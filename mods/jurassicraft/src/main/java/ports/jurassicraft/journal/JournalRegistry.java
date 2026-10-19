package ports.jurassicraft.journal;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;

public final class JournalRegistry {
    public static final Supplier<Item> JOURNAL = Platform.register(Registry.ITEM_REGISTRY, "ingen_journal", JournalItem::new);
    private JournalRegistry() {}
    public static void init() { }
}
