package ports.jurassicraft.bugs;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.feeder.FeederRegistry;

/** Live-world checks for sided inventories, breeding, persistence and feeding. */
public final class BugFeederGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(1,1,1);
    public BugFeederGameTests() {}
    private static void check(GameTestHelper h,boolean condition,String message) { if (!condition) h.fail(message); }
    private static void tick(GameTestHelper h,BugCrateBlockEntity crate,int count) {
        for (int i=0;i<count;i++) BugCrateBlockEntity.tick(h.getLevel(),crate.getBlockPos(),crate.getBlockState(),crate);
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void breedingAndSidedInventorySurviveReload(GameTestHelper h) {
        h.setBlock(POS,BugRegistry.CRATE.get());
        var crate=(BugCrateBlockEntity)h.getBlockEntity(POS);
        check(h,crate.canPlaceItemThroughFace(0,new ItemStack(BugRegistry.CRICKETS.get()),Direction.UP),"top must accept insects");
        check(h,crate.canPlaceItemThroughFace(4,new ItemStack(Items.HAY_BLOCK),Direction.NORTH),"sides must accept food");
        check(h,!crate.canPlaceItemThroughFace(6,new ItemStack(BugRegistry.CRICKETS.get()),Direction.DOWN),"output must reject inserted insects");
        check(h,crate.canTakeItemThroughFace(6,ItemStack.EMPTY,Direction.DOWN) && !crate.canTakeItemThroughFace(0,ItemStack.EMPTY,Direction.UP),"automation must extract only outputs");
        crate.setItem(0,new ItemStack(BugRegistry.CRICKETS.get()));
        crate.setItem(3,new ItemStack(Items.WHEAT)); crate.setItem(4,new ItemStack(Items.HAY_BLOCK));
        tick(h,crate,1600);
        var loaded=new BugCrateBlockEntity(crate.getBlockPos(),crate.getBlockState()); loaded.load(crate.saveWithoutMetadata());
        tick(h,loaded,2000);
        check(h,loaded.getItem(0).is(BugRegistry.CRICKETS.get()) && loaded.getItem(0).getCount()==1,"breeding must retain its starter");
        check(h,loaded.getItem(3).is(Items.WHEAT) && loaded.getItem(4).isEmpty(),"best available food must be consumed first");
        check(h,loaded.getItem(6).is(BugRegistry.CRICKETS.get()) && loaded.getItem(6).getCount()==27,"hay must produce 27 crickets after 3600 ticks including reload");
        h.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void replacementResetsCycleAndSwarmNeedsWater(GameTestHelper h) {
        h.setBlock(POS,BugRegistry.CRATE.get());
        var crate=(BugCrateBlockEntity)h.getBlockEntity(POS);
        crate.setItem(0,new ItemStack(BugRegistry.COCKROACHES.get())); crate.setItem(3,new ItemStack(Items.MELON));
        tick(h,crate,3599); crate.setItem(3,new ItemStack(Items.HAY_BLOCK)); tick(h,crate,1);
        check(h,crate.getItem(6).isEmpty(),"replacing food must reset the previous breeding cycle");
        tick(h,crate,1999);
        check(h,crate.getItem(6).getCount()==16 && crate.getItem(3).isEmpty(),"hay must produce 16 cockroaches in 2000 ticks");
        BlockPos water=h.absolutePos(new BlockPos(3,1,3)), above=water.above();
        h.getLevel().setBlock(water,Blocks.WATER.defaultBlockState(),3);
        h.getLevel().setBlock(above,BugRegistry.KRILL_SWARM.get().defaultBlockState(),3);
        check(h,h.getLevel().getBlockState(above).canSurvive(h.getLevel(),above),"swarm must survive above water");
        h.getLevel().setBlock(water,Blocks.STONE.defaultBlockState(),3);
        check(h,h.getLevel().getBlockState(above).isAir(),"swarm must break when water is removed");
        h.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
    public void feederAutomationPersistenceAndClientUpdates(GameTestHelper h) {
        h.setBlock(POS,FeederRegistry.BLOCK.get());
        var feeder=(FeederBlockEntity)h.getBlockEntity(POS);
        var meat=new ItemStack(Items.BEEF,8); var plant=new ItemStack(Items.WHEAT,8);
        for (Direction face : Direction.values()) {
            check(h,feeder.canPlaceItemThroughFace(0,meat,face) && !feeder.canPlaceItemThroughFace(9,meat,face),"automation must put meat only in meat slots");
            check(h,feeder.canPlaceItemThroughFace(9,plant,face) && !feeder.canPlaceItemThroughFace(0,plant,face),"automation must put plants only in plant slots");
            check(h,!feeder.canTakeItemThroughFace(0,meat,face),"automation cannot extract feeder contents");
        }
        feeder.setItem(0,meat); feeder.setItem(9,plant);
        check(h,feeder.comparatorSignal()>0,"comparator must detect food");
        var restored=new FeederBlockEntity(feeder.getBlockPos(),feeder.getBlockState()); restored.load(feeder.saveWithoutMetadata());
        check(h,restored.getItem(0).is(Items.BEEF) && restored.getItem(0).getCount()==8,"meat must persist");
        check(h,restored.getItem(9).is(Items.WHEAT) && restored.getItem(9).getCount()==8,"plants must persist");
        CompoundTag update=new CompoundTag(); update.putBoolean("VisualOpen",true); restored.load(update);
        check(h,restored.getItem(0).getCount()==8,"visual-only updates must not erase inventory");
        for (int i=0;i<25;i++) FeederBlockEntity.clientTick(h.getLevel(),restored.getBlockPos(),restored.getBlockState(),restored);
        check(h,restored.lidOpening(1)>.99F,"open visual update must animate lids");
        update.putBoolean("VisualOpen",false); restored.load(update);
        for (int i=0;i<25;i++) FeederBlockEntity.clientTick(h.getLevel(),restored.getBlockPos(),restored.getBlockState(),restored);
        check(h,restored.lidOpening(1)==0,"closed visual update must animate lids closed");
        h.succeed();
    }
    @GameTest(template=FabricGameTest.EMPTY_STRUCTURE,timeoutTicks=80)
    public void feederServesSuitableDinosaur(GameTestHelper h) {
        for (int x=0;x<5;x++) for (int z=0;z<5;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        h.setBlock(POS,FeederRegistry.BLOCK.get());
        var feeder=(FeederBlockEntity)h.getBlockEntity(POS);
        feeder.setItem(0,new ItemStack(Items.BEEF,2)); feeder.setItem(9,new ItemStack(Items.CARROT,2));
        DinosaurEntity dinosaur=DinosaurRegistry.type(Species.TRICERATOPS).create(h.getLevel());
        if (dinosaur==null) { h.fail("could not create dinosaur"); return; }
        dinosaur.setNoAi(true);
        BlockPos near=h.absolutePos(new BlockPos(1,1,2)); dinosaur.moveTo(near.getX()+.5,near.getY(),near.getZ()+.5,0,0);
        CompoundTag state=new CompoundTag(); dinosaur.addAdditionalSaveData(state); state.putInt("Energy",0); state.putInt("DigestingFood",0); dinosaur.readAdditionalSaveData(state);
        h.getLevel().addFreshEntity(dinosaur);
        check(h,dinosaur.needsFood(),"test dinosaur must be hungry");
        check(h,feeder.hasFoodFor(dinosaur),"feeder must offer food suitable for this herbivore");
        h.succeedWhen(() -> {
            check(h,feeder.getItem(9).getCount()==1,"feeder must consume one suitable food");
            check(h,feeder.getItem(0).getCount()==2,"herbivore must not consume meat");
            check(h,feeder.getUpdateTag().getBoolean("VisualOpen") && !feeder.getUpdateTag().contains("Inventory"),"feeding must open the visual lids without sending inventory");
            dinosaur.discard();
        });
    }
}
