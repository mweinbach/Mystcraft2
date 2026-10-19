package ports.jurassicraft.fieldguide;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class FieldGuideRegistry {
    public static final Supplier<Item> FIELD_GUIDE = Platform.register(Registry.ITEM_REGISTRY, "field_guide",
            () -> new FieldGuideItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));

    private FieldGuideRegistry() {}
    public static void init() {}
}
