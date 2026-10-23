package ports.jurassicraft.music;

import java.util.HashSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.gametest.TestPlatform;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

public final class MusicGameTests {
    private MusicGameTests() {}
    private static void check(GameTestHelper helper,boolean value,String message) { if (!value) helper.fail(message); }
    private static void check(GameTestHelper helper, Item item, SoundEvent sound, int ticks, int signal) {
        if (!(item instanceof RecordItem record) || record.getSound() != sound
                || record.getLengthInTicks() != ticks || record.getAnalogOutput() != signal
                || RecordItem.getBySound(sound) != (TestPlatform.get().recordsIndexedBySound() ? record : null)) {
            helper.fail("record sound, duration, comparator output or lookup is incorrect");
        }
    }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void recordsAndChestLoot(GameTestHelper h) {
        check(h,MusicRegistry.THEME.get() instanceof RecordItem && ((RecordItem)MusicRegistry.THEME.get()).getSound()==MusicRegistry.THEME_SOUND.get(),"theme disc must play its registered sound");
        check(h,((RecordItem)MusicRegistry.THEME.get()).getLengthInTicks()==4749 && ((RecordItem)MusicRegistry.TROODONS.get()).getLengthInTicks()==1765 && ((RecordItem)MusicRegistry.MUSCLE.get()).getLengthInTicks()==2044,"jukebox durations must match the original audio");
        LootTable table=TestPlatform.get().loot(sink -> MusicLoot.addTo(new ResourceLocation("minecraft","chests/simple_dungeon"),sink,100));
        check(h,table!=null,"dungeon loot must receive the record pool");
        var context=new LootContext.Builder(h.getLevel()).withRandom(RandomSource.create(42)).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        var seen=new HashSet<net.minecraft.world.item.Item>();
        for (int i=0;i<100;i++) for (var stack : table.getRandomItems(context)) seen.add(stack.getItem());
        check(h,seen.contains(MusicRegistry.THEME.get()) && seen.contains(MusicRegistry.TROODONS.get()) && seen.contains(MusicRegistry.MUSCLE.get()),"all three original discs must be obtainable");
        LootTable unrelated=TestPlatform.get().loot(sink -> MusicLoot.addTo(new ResourceLocation("minecraft","chests/end_city_treasure"),sink,100));
        check(h,unrelated==null,"unrelated chest categories must remain unchanged");
        LootTable disabled=TestPlatform.get().loot(sink -> MusicLoot.addTo(new ResourceLocation("minecraft","chests/simple_dungeon"),sink,0));
        for (int i=0;i<20;i++) check(h,disabled==null || disabled.getRandomItems(context).isEmpty(),"zero loot percentage must disable record drops");
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void recordsKeepTheirExactDurations(GameTestHelper helper) {
        check(helper, MusicRegistry.THEME.get(), MusicRegistry.THEME_SOUND.get(), 4749, 1);
        check(helper, MusicRegistry.TROODONS.get(), MusicRegistry.TROODONS_SOUND.get(), 1765, 2);
        check(helper, MusicRegistry.MUSCLE.get(), MusicRegistry.MUSCLE_SOUND.get(), 2044, 3);
        helper.succeed();
    }
}
