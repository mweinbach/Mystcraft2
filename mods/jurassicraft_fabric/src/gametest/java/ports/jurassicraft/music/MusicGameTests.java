package ports.jurassicraft.music;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;

public final class MusicGameTests implements FabricGameTest {
    private static void check(GameTestHelper helper, Item item, SoundEvent sound, int ticks, int signal) {
        if (!(item instanceof RecordItem record) || record.getSound() != sound
                || record.getLengthInTicks() != ticks || record.getAnalogOutput() != signal
                || RecordItem.getBySound(sound) != record) {
            helper.fail("record sound, duration, comparator output or lookup is incorrect");
        }
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void recordsKeepTheirExactDurations(GameTestHelper helper) {
        check(helper, MusicRegistry.THEME.get(), MusicRegistry.THEME_SOUND.get(), 4749, 1);
        check(helper, MusicRegistry.TROODONS.get(), MusicRegistry.TROODONS_SOUND.get(), 1765, 2);
        check(helper, MusicRegistry.MUSCLE.get(), MusicRegistry.MUSCLE_SOUND.get(), 2044, 3);
        helper.succeed();
    }
}
