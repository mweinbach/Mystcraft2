package ports.jurassicraft.music;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Original playable records. */
public final class MusicRegistry {
    private static SoundEvent themeSound, troodonsSound, muscleSound;
    private static Item theme, troodons, muscle;
    public static final Supplier<SoundEvent> THEME_SOUND = () -> themeSound;
    public static final Supplier<SoundEvent> TROODONS_SOUND = () -> troodonsSound;
    public static final Supplier<SoundEvent> MUSCLE_SOUND = () -> muscleSound;
    public static final Supplier<Item> THEME = () -> theme;
    public static final Supplier<Item> TROODONS = () -> troodons;
    public static final Supplier<Item> MUSCLE = () -> muscle;
    private MusicRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static SoundEvent sound(String path) { ResourceLocation id = id(path); return Registry.register(Registry.SOUND_EVENT, id, new SoundEvent(id)); }
    private static Item record(String path, SoundEvent sound, int ticks, int signal) {
        return Registry.register(Registry.ITEM, id(path), new MusicRecordItem(signal, sound, ticks));
    }

    /** Vanilla's constructor takes seconds, but the source lengths are exact ticks. */
    private static final class MusicRecordItem extends RecordItem {
        private final int ticks;
        private MusicRecordItem(int signal, SoundEvent sound, int ticks) {
            super(signal, sound, new Item.Properties().tab(JurassicContent.TAB).stacksTo(1), (ticks + 19) / 20);
            this.ticks = ticks;
        }
        @Override public int getLengthInTicks() { return ticks; }
    }
    public static void register() {
        themeSound = sound("jurassicraft_theme"); troodonsSound = sound("troodons_and_raptors"); muscleSound = sound("dont_move_a_muscle");
        theme = record("disc_jurassicraft_theme", themeSound, 4749, 1);
        troodons = record("disc_troodons_and_raptors", troodonsSound, 1765, 2);
        muscle = record("disc_dont_move_a_muscle", muscleSound, 2044, 3);
        MusicLoot.register();
    }
}
