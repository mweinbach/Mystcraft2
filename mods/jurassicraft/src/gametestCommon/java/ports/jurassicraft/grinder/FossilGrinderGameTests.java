package ports.jurassicraft.grinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;

/** Opt-in server integration checks. */
public final class FossilGrinderGameTests {
    private static final BlockPos POS=new BlockPos(1,1,1);
    private FossilGrinderGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }
    private static FossilGrinderBlockEntity place(GameTestHelper h) {
        h.setBlock(POS,FossilGrinderRegistry.FOSSIL_GRINDER.get());
        return (FossilGrinderBlockEntity)h.getBlockEntity(POS);
    }
    private static void tick(GameTestHelper h,FossilGrinderBlockEntity machine,int count) {
        for (int i=0;i<count;i++) FossilGrinderBlockEntity.tick(h.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine);
    }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void fossilOddsAndMetadata(GameTestHelper h) {
        ItemStack fossil=new ItemStack(JurassicContent.BONES.get(Species.VELOCIRAPTOR).get("skull").get());
        fossil.getOrCreateTag().putString("SampleMarker","retained");
        RandomSource random=RandomSource.create(1234);
        int meal=0, flint=0, tissue=0;
        for (int i=0;i<1200;i++) {
            ItemStack out=FossilGrinderRecipes.grind(fossil,random);
            if (out.is(Items.BONE_MEAL)) meal++;
            else if (out.is(Items.FLINT)) flint++;
            else if (out.is(GeneticsRegistry.softTissue(Species.VELOCIRAPTOR).get())) {
                tissue++;
                check(h,"retained".equals(out.getTag().getString("SampleMarker")),"soft tissue must copy input NBT");
            } else h.fail("unexpected fossil output");
        }
        check(h,meal>500 && meal<700 && flint>300 && flint<500 && tissue>140 && tissue<260,"fossil outputs must follow the upstream 3:2:1 weighting");
        h.succeed();
    }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void persistenceReplacementAndAutomation(GameTestHelper h) {
        FossilGrinderBlockEntity grinder=place(h);
        var input=TestPlatform.automation(grinder, Direction.UP);
        var output=TestPlatform.automation(grinder, Direction.DOWN);
        ItemStack skull=new ItemStack(JurassicContent.BONES.get(Species.VELOCIRAPTOR).get("skull").get());
        check(h,input.insert(0,skull,false).isEmpty(),"top must accept fossil input");
        check(h,!output.insert(0,new ItemStack(Items.STONE),false).isEmpty(),"bottom must reject insertion");
        tick(h,grinder,100);
        var saved=grinder.saveWithoutMetadata();
        FossilGrinderBlockEntity restored=new FossilGrinderBlockEntity(grinder.getBlockPos(),grinder.getBlockState());
        restored.load(saved); tick(h,restored,100);
        check(h,restored.getItem(0).isEmpty() && !restored.getItem(6).isEmpty(),"processing progress must survive reload");
        h.setBlock(POS,Blocks.AIR);
        grinder=place(h);
        grinder.setItem(0,skull.copy()); tick(h,grinder,100);
        grinder.setItem(0,new ItemStack(JurassicContent.BONES.get(Species.TRICERATOPS).get("skull").get()));
        tick(h,grinder,100);
        check(h,!grinder.getItem(0).isEmpty() && grinder.getItem(6).isEmpty(),"replacement input must not inherit progress");
        tick(h,grinder,100);
        check(h,grinder.getItem(0).isEmpty() && !grinder.getItem(6).isEmpty(),"replacement must process after a full cycle");
        h.succeed();
    }
}
