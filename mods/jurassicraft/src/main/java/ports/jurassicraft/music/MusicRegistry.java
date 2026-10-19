package ports.jurassicraft.music;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Original playable JurassiCraft records; lengths are rounded up to full ticks. */
public final class MusicRegistry {
    public static final Supplier<SoundEvent> THEME_SOUND=sound("jurassicraft_theme");
    public static final Supplier<SoundEvent> TROODONS_SOUND=sound("troodons_and_raptors");
    public static final Supplier<SoundEvent> MUSCLE_SOUND=sound("dont_move_a_muscle");
    public static final Supplier<Item> THEME=record("disc_jurassicraft_theme",THEME_SOUND,4749,1);
    public static final Supplier<Item> TROODONS=record("disc_troodons_and_raptors",TROODONS_SOUND,1765,2);
    public static final Supplier<Item> MUSCLE=record("disc_dont_move_a_muscle",MUSCLE_SOUND,2044,3);
    private MusicRegistry() {}
    private static Supplier<SoundEvent> sound(String id) { return Platform.register(Registry.SOUND_EVENT_REGISTRY, id,() -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID,id))); }
    private static Supplier<Item> record(String id,Supplier<SoundEvent> sound,int ticks,int signal) {
        return Platform.register(Registry.ITEM_REGISTRY, id,() -> Platform.recordItem(signal,sound,new Item.Properties().tab(JurassicContent.TAB).stacksTo(1),ticks));
    }
    public static void init() { }
}
