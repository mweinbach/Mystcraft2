package ports.jurassicraft.hatching;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;

/** Opt-in integration checks. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HatcheryGameTests {
    private static final BlockPos POS = new BlockPos(1,1,1);
    private HatcheryGameTests() {}
    private static void check(GameTestHelper h, boolean ok, String message) { if (!ok) h.fail(message); }
    private static HatcheryBlockEntity place(GameTestHelper h, HatcheryRegistry.Machine kind) {
        h.setBlock(POS,HatcheryRegistry.block(kind));
        return (HatcheryBlockEntity) h.getBlockEntity(POS);
    }
    private static void tick(GameTestHelper h,HatcheryBlockEntity machine,int count) {
        for (int i=0;i<count;i++) HatcheryBlockEntity.tick(h.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine);
    }
    private static ItemStack tube(DNAData dna) {
        ItemStack result=new ItemStack(GeneticsRegistry.dinosaurDna(dna.dinosaur()).get());
        Genetics.write(result.getOrCreateTag(),dna); result.getOrCreateTag().putString("Extra", "preserve"); return result;
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void embryoChainAndPersistence(GameTestHelper h) {
        DNAData dna=DNAData.dinosaur(Species.VELOCIRAPTOR,100,"ACGTACGTAC");
        ItemStack tube=tube(dna), wrong=new ItemStack(GeneticsRegistry.dinosaurDna(Species.TRICERATOPS).get());
        wrong.setTag(tube.getTag().copy());
        ItemStack dish=new ItemStack(ComponentsRegistry.PETRI_DISH.get()), empty=new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get());
        check(h,HatcheryRecipes.embryonic(wrong,dish,empty).isEmpty(),"mismatched tube must be rejected");
        HatcheryBlockEntity machine=place(h,HatcheryRegistry.Machine.EMBRYONIC);
        machine.setItem(0,tube); machine.setItem(1,dish); machine.setItem(2,empty);
        tick(h,machine,90);
        var saved=machine.saveWithoutMetadata();
        HatcheryBlockEntity restored=new HatcheryBlockEntity(machine.getBlockPos(),machine.getBlockState(),HatcheryRegistry.Machine.EMBRYONIC);
        restored.load(saved); tick(h,restored,110);
        ItemStack embryo=restored.getItem(3);
        check(h,embryo.is(HatcheryRegistry.syringe(Species.VELOCIRAPTOR)),"persistent embryonic process must complete");
        check(h,Genetics.read(embryo.getTag()).filter(dna::equals).isPresent() && "preserve".equals(embryo.getTag().getString("Extra")),"syringe must retain DNA and metadata");
        machine=place(h,HatcheryRegistry.Machine.CALCIFIER);
        machine.setItem(0,embryo.copy()); machine.setItem(1,new ItemStack(Items.EGG));
        tick(h,machine,200);
        ItemStack egg=machine.getItem(2);
        check(h,egg.is(HatcheryRegistry.egg(Species.VELOCIRAPTOR)) && egg.getTag().equals(embryo.getTag()),"calcification must carry DNA to the correct egg");
        ItemStack hatched=HatcheryRecipes.incubate(egg,75);
        check(h,hatched.is(HatcheryRegistry.hatchedEgg(Species.VELOCIRAPTOR)) && hatched.getTag().getBoolean("Gender") && Genetics.read(hatched.getTag()).filter(dna::equals).isPresent(),"incubation must preserve DNA and use temperature for sex");
        h.succeed();
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void incubatorVisualSlots(GameTestHelper h) {
        HatcheryBlockEntity incubator=place(h,HatcheryRegistry.Machine.INCUBATOR);
        ItemStack egg=new ItemStack(HatcheryRegistry.egg(Species.TRICERATOPS));
        egg.getOrCreateTag().putString("Sample", "visible");
        incubator.setItem(4,egg.copy());
        incubator.setItem(5,new ItemStack(HatcheryRegistry.PEAT_MOSS_ITEM.get()));
        var tag=incubator.getUpdateTag();
        check(h,!tag.contains("Items") && !tag.contains("Visual5"),"update must omit the fuel and private inventory");
        HatcheryBlockEntity client=new HatcheryBlockEntity(incubator.getBlockPos(),incubator.getBlockState(),HatcheryRegistry.Machine.INCUBATOR);
        client.handleUpdateTag(tag);
        check(h,ItemStack.isSameItemSameTags(client.visualEgg(4),egg) && client.visualEgg(0).isEmpty(),"visible eggs must retain their species and metadata");
        incubator.removeItemNoUpdate(4);
        client.handleUpdateTag(incubator.getUpdateTag());
        check(h,client.visualEgg(4).isEmpty(),"removing an egg must clear its visual slot");
        h.succeed();
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void incubatorAndTemperatureButtons(GameTestHelper h) {
        DNAData dna=DNAData.dinosaur(Species.TRICERATOPS,100,"TTGGCCAATT");
        ItemStack embryo=HatcheryRecipes.embryonic(tube(dna),new ItemStack(ComponentsRegistry.PETRI_DISH.get()),new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get()));
        ItemStack egg=HatcheryRecipes.calcify(embryo,new ItemStack(Items.EGG));
        HatcheryBlockEntity machine=place(h,HatcheryRegistry.Machine.INCUBATOR);
        machine.setItem(0,egg.copy()); machine.setItem(1,egg.copy()); machine.setItem(5,new ItemStack(HatcheryRegistry.PEAT_MOSS_ITEM.get()));
        var player=h.makeMockPlayer(); BlockPos absolute=h.absolutePos(POS); player.moveTo(absolute.getX()+.5,absolute.getY()+.5,absolute.getZ()+.5);
        HatcheryMenu menu=(HatcheryMenu)machine.createMenu(1,player.getInventory(),player);
        check(h,menu.clickMenuButton(player,15) && menu.temperature(0)==75,"temperature packet must use a valid signed-byte button ID");
        check(h,!menu.clickMenuButton(player,105),"out-of-range temperature button must be rejected");
        tick(h,machine,4000);
        var saved=machine.saveWithoutMetadata();
        HatcheryBlockEntity restored=new HatcheryBlockEntity(machine.getBlockPos(),machine.getBlockState(),HatcheryRegistry.Machine.INCUBATOR);
        restored.load(saved); tick(h,restored,4000);
        check(h,restored.getItem(0).is(HatcheryRegistry.hatchedEgg(Species.TRICERATOPS)) && restored.getItem(0).getTag().getBoolean("Gender"),"incubation must retain progress and temperature across reload");
        check(h,restored.getItem(1).is(HatcheryRegistry.egg(Species.TRICERATOPS)) && restored.getItem(5).isEmpty(),"one moss can incubate only one egg");
        restored.setItem(5,new ItemStack(HatcheryRegistry.PEAT_MOSS_ITEM.get())); tick(h,restored,2);
        check(h,restored.getItem(1).is(HatcheryRegistry.hatchedEgg(Species.TRICERATOPS)),"a second moss should finish the other egg");
        h.succeed();
    }
}
