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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MusicGameTests {
    private MusicGameTests() {}
    private static void check(GameTestHelper helper,boolean value,String message) { if (!value) helper.fail(message); }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void recordsAndChestLoot(GameTestHelper h) {
        check(h,MusicRegistry.THEME.get() instanceof RecordItem && ((RecordItem)MusicRegistry.THEME.get()).getSound()==MusicRegistry.THEME_SOUND.get(),"theme disc must play its registered sound");
        check(h,((RecordItem)MusicRegistry.THEME.get()).getLengthInTicks()==4749 && ((RecordItem)MusicRegistry.TROODONS.get()).getLengthInTicks()==1765 && ((RecordItem)MusicRegistry.MUSCLE.get()).getLengthInTicks()==2044,"jukebox durations must match the original audio");
        LootTable table=LootTable.lootTable().setParamSet(LootContextParamSets.CHEST).build();
        MusicLoot.addTo(new ResourceLocation("minecraft","chests/simple_dungeon"),table);
        MusicLoot.addTo(new ResourceLocation("minecraft","chests/simple_dungeon"),table);
        check(h,table.getPool(MusicLoot.POOL)!=null,"dungeon loot must receive the record pool");
        var context=new LootContext.Builder(h.getLevel()).withRandom(RandomSource.create(42)).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        var seen=new HashSet<net.minecraft.world.item.Item>();
        for (int i=0;i<100;i++) for (var stack : table.getRandomItems(context)) seen.add(stack.getItem());
        check(h,seen.contains(MusicRegistry.THEME.get()) && seen.contains(MusicRegistry.TROODONS.get()) && seen.contains(MusicRegistry.MUSCLE.get()),"all three original discs must be obtainable");
        LootTable unrelated=LootTable.lootTable().build();
        MusicLoot.addTo(new ResourceLocation("minecraft","chests/end_city_treasure"),unrelated);
        check(h,unrelated.getPool(MusicLoot.POOL)==null,"unrelated chest categories must remain unchanged");
        LootTable disabled=LootTable.lootTable().setParamSet(LootContextParamSets.CHEST).build();
        MusicLoot.addTo(new ResourceLocation("minecraft","chests/simple_dungeon"),disabled,0);
        for (int i=0;i<20;i++) check(h,disabled.getRandomItems(context).isEmpty(),"zero loot percentage must disable record drops");
        h.succeed();
    }
}
