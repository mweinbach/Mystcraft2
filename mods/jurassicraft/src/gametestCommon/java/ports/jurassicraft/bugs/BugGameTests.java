package ports.jurassicraft.bugs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.gametest.TestPlatform;

public final class BugGameTests {
    private static final BlockPos POS=new BlockPos(1,1,1);
    private BugGameTests() {}
    private static void check(GameTestHelper h,boolean value,String message) { if (!value) h.fail(message); }
    private static void tick(GameTestHelper h,BugCrateBlockEntity crate,int count) {
        for (int i=0;i<count;i++) BugCrateBlockEntity.tick(h.getLevel(),crate.getBlockPos(),crate.getBlockState(),crate);
    }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void bestFoodBreedsAndPersists(GameTestHelper h) {
        h.setBlock(POS,BugRegistry.CRATE.get());
        var crate=(BugCrateBlockEntity)h.getBlockEntity(POS);
        var input=TestPlatform.automation(crate, Direction.UP);
        var output=TestPlatform.automation(crate, Direction.DOWN);
        check(h,input.insert(0,new ItemStack(BugRegistry.CRICKETS.get()),false).isEmpty(),"automation must accept insects");
        check(h,input.insert(3,new ItemStack(Items.WHEAT),false).isEmpty(),"automation must accept bug food");
        check(h,input.insert(4,new ItemStack(Items.HAY_BLOCK),false).isEmpty(),"automation must accept hay");
        check(h,output.insert(0,new ItemStack(BugRegistry.CRICKETS.get()),false).getCount()==1,"automation must not insert into outputs");
        tick(h,crate,1600);
        var tag=crate.saveWithoutMetadata();
        BugCrateBlockEntity loaded=new BugCrateBlockEntity(crate.getBlockPos(),crate.getBlockState()); loaded.load(tag);
        tick(h,loaded,2000);
        check(h,loaded.getItem(0).is(BugRegistry.CRICKETS.get()) && loaded.getItem(0).getCount()==1,"breeding must preserve the insect starter");
        check(h,loaded.getItem(3).is(Items.WHEAT) && loaded.getItem(4).isEmpty(),"the best food must be consumed first");
        check(h,loaded.getItem(6).is(BugRegistry.CRICKETS.get()) && loaded.getItem(6).getCount()==27,"hay must produce 27 crickets after 3600 ticks including reload");
        h.succeed();
    }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void replacedFoodResetsProgress(GameTestHelper h) {
        h.setBlock(POS,BugRegistry.CRATE.get());
        var crate=(BugCrateBlockEntity)h.getBlockEntity(POS);
        crate.setItem(0,new ItemStack(BugRegistry.COCKROACHES.get())); crate.setItem(3,new ItemStack(Items.MELON));
        tick(h,crate,3599); crate.setItem(3,new ItemStack(Items.HAY_BLOCK)); tick(h,crate,1);
        check(h,crate.getItem(6).isEmpty(),"changing food must reset the previous breeding cycle");
        tick(h,crate,1999);
        check(h,crate.getItem(6).getCount()==16 && crate.getItem(3).isEmpty(),"hay must breed 16 cockroaches in 2000 ticks");
        h.succeed();
    }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void swarmRequiresWater(GameTestHelper h) {
        h.setBlock(POS,Blocks.WATER); BlockPos water=h.absolutePos(POS), above=water.above();
        h.getLevel().setBlock(above,BugRegistry.KRILL_SWARM.get().defaultBlockState(),3);
        check(h,h.getLevel().getBlockState(above).canSurvive(h.getLevel(),above),"swarm must survive above water");
        h.getLevel().setBlock(water,Blocks.STONE.defaultBlockState(),3);
        check(h,h.getLevel().getBlockState(above).isAir(),"swarm must drop when water is removed");
        h.succeed();
    }
}
