package ports.jurassicraft.fieldguide;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class FieldGuideRegistry {
    private static Item fieldGuide;
    public static final Supplier<Item> FIELD_GUIDE = () -> fieldGuide;
    private FieldGuideRegistry() {}
    public static void init() {
        fieldGuide = Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, "field_guide"),
                new FieldGuideItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    }
}
