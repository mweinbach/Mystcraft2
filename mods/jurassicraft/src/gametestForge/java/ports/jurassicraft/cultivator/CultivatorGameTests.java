package ports.jurassicraft.cultivator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.supplies.DinosaurSupplies;

/** Opt-in server tests. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CultivatorGameTests {
    private static final BlockPos POS=new BlockPos(1,1,1);
    private CultivatorGameTests() {}
    private static void check(GameTestHelper h,boolean value,String message) { if (!value) h.fail(message); }
    private static void tick(GameTestHelper h,CultivatorBlockEntity entity,int ticks) {
        for (int i=0;i<ticks;i++) CultivatorBlockEntity.tick(h.getLevel(),entity.getBlockPos(),entity.getBlockState(),entity);
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void waterNutrientsGestationAndPersistence(GameTestHelper h) {
        var steak=CultivatorNutrition.get(new ItemStack(DinosaurSupplies.STEAK.get()));
        check(h,steak!=null && steak.proximates()==.580 && steak.minerals()==.390 && steak.vitamins()==.490 && steak.lipids()==.470,"dinosaur steak must provide its original nutrients");
        for (Species species : Species.values()) {
            var raw=CultivatorNutrition.get(new ItemStack(DinosaurRegistry.rawMeat(species).get()));
            var cooked=CultivatorNutrition.get(new ItemStack(DinosaurRegistry.cookedMeat(species).get()));
            check(h,raw!=null && raw.proximates()==.460 && cooked!=null && cooked.proximates()==.580,"species-specific meat must provide the original nutrients");
        }
        h.setBlock(POS,CultivatorRegistry.CULTIVATOR.get());
        CultivatorBlockEntity machine=(CultivatorBlockEntity)h.getBlockEntity(POS);
        DNAData dna=DNAData.dinosaur(Species.COELACANTH,100,"ACGTACGTAC");
        ItemStack syringe=new ItemStack(HatcheryRegistry.syringe(Species.COELACANTH));
        Genetics.write(syringe.getOrCreateTag(),dna); syringe.getTag().putString("Extra","preserve");
        var input=machine.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(IllegalStateException::new);
        var output=machine.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow(IllegalStateException::new);
        check(h,input.insertItem(0,syringe,false).isEmpty(),"cultivator must accept coelacanth embryo");
        check(h,output.extractItem(0,1,false).isEmpty(),"automation must not take an ungestated syringe");
        machine.setItem(1,new ItemStack(Items.COOKED_CHICKEN,20));
        machine.setItem(2,new ItemStack(Items.WATER_BUCKET)); tick(h,machine,1);
        machine.setItem(2,new ItemStack(Items.WATER_BUCKET)); tick(h,machine,19);
        var filled=machine.saveWithoutMetadata();
        check(h,filled.getInt("Water")==2 && machine.getItem(3).is(Items.BUCKET) && machine.getItem(3).getCount()==2,"two buckets must fill the cultivator and return empties");
        for (String n : new String[]{"Proximates","Minerals","Vitamins","Lipids"}) check(h,filled.getInt(n)>=1500,"food must supply "+n);
        machine.setTemperature(75); tick(h,machine,900);
        var saved=machine.saveWithoutMetadata();
        CultivatorBlockEntity restored=new CultivatorBlockEntity(machine.getBlockPos(),machine.getBlockState()); restored.load(saved);
        tick(h,restored,2000);
        ItemStack result=restored.getItem(0);
        check(h,result.is(HatcheryRegistry.hatchedEgg(Species.COELACANTH)) && result.getTag().getBoolean("Gender"),"gestation must produce a male coelacanth");
        check(h,Genetics.read(result.getTag()).filter(dna::equals).isPresent() && "preserve".equals(result.getTag().getString("Extra")),"gestation must preserve genetic metadata");
        check(h,restored.saveWithoutMetadata().getInt("Water")==1,"gestation consumes one water level");
        h.succeed();
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void bothHalvesUseOneMachine(GameTestHelper h) {
        h.setBlock(POS,CultivatorRegistry.CULTIVATOR.get());
        BlockPos p=h.absolutePos(POS);
        var state=h.getLevel().getBlockState(p);
        CultivatorRegistry.CULTIVATOR.get().setPlacedBy(h.getLevel(),p,state,null,new ItemStack(CultivatorRegistry.ITEM.get()));
        check(h,h.getLevel().getBlockState(p.above()).is(CultivatorRegistry.CULTIVATOR.get()) && h.getLevel().getBlockState(p.above()).getValue(CultivatorBlock.HALF)==DoubleBlockHalf.UPPER,"placement must create the upper half");
        check(h,h.getLevel().getBlockEntity(p)!=null && h.getLevel().getBlockEntity(p.above())==null,"only the lower half may own inventory");
        h.getLevel().destroyBlock(p.above(),true);
        check(h,h.getLevel().getBlockState(p).isAir(),"breaking the upper half must remove the lower half");
        h.succeed();
    }
}
