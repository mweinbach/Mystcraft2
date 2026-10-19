package ports.jurassicraft.music;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Original playable JurassiCraft records; lengths are rounded up to full ticks. */
public final class MusicRegistry {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,PortMod.MOD_ID);
    public static final RegistryObject<SoundEvent> THEME_SOUND=sound("jurassicraft_theme");
    public static final RegistryObject<SoundEvent> TROODONS_SOUND=sound("troodons_and_raptors");
    public static final RegistryObject<SoundEvent> MUSCLE_SOUND=sound("dont_move_a_muscle");
    public static final RegistryObject<Item> THEME=record("disc_jurassicraft_theme",THEME_SOUND,4749,1);
    public static final RegistryObject<Item> TROODONS=record("disc_troodons_and_raptors",TROODONS_SOUND,1765,2);
    public static final RegistryObject<Item> MUSCLE=record("disc_dont_move_a_muscle",MUSCLE_SOUND,2044,3);
    private MusicRegistry() {}
    private static RegistryObject<SoundEvent> sound(String id) { return SOUNDS.register(id,() -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID,id))); }
    private static RegistryObject<Item> record(String id,RegistryObject<SoundEvent> sound,int ticks,int signal) {
        return ITEMS.register(id,() -> new RecordItem(signal,sound,new Item.Properties().tab(JurassicContent.TAB).stacksTo(1),ticks));
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); ITEMS.register(bus); }
}
