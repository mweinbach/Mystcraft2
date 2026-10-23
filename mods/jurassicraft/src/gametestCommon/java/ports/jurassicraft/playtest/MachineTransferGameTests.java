package ports.jurassicraft.playtest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.bugs.BugCrateBlockEntity;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.cultivator.CultivatorBlockEntity;
import ports.jurassicraft.cultivator.CultivatorRegistry;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.feeder.FeederRegistry;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineBlockEntity;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Item automation (Forge capabilities, Fabric Transfer API) for machines that other mods insert into by face. */
public final class MachineTransferGameTests {
    public MachineTransferGameTests() {}

    private static void check(GameTestHelper helper, boolean value, String message) {
        if (!value) helper.fail(message);
    }

    private static TestPlatform.ItemAccess sided(GameTestHelper helper, BlockPos pos) {
        return TestPlatform.get().items(helper.getBlockEntity(pos), Direction.UP);
    }

    /** Inserts the whole stack as a hopper would and returns how many items were accepted. */
    private static int commit(TestPlatform.ItemAccess access, ItemStack stack) {
        return stack.getCount() - access.insert(stack, false).getCount();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void sidedTransfersReachFeederCrateCultivatorAndLab(GameTestHelper helper) {
        BlockPos feederPos = new BlockPos(0, 1, 0);
        helper.setBlock(feederPos, FeederRegistry.BLOCK.get());
        var feeder = (FeederBlockEntity) helper.getBlockEntity(feederPos);
        TestPlatform.ItemAccess feederStorage = sided(helper, feederPos);
        check(helper, feederStorage != null, "feeder must expose item automation");
        check(helper, commit(feederStorage, new ItemStack(Items.BEEF, 4)) == 4, "feeder must accept meat");
        check(helper, feeder.getItem(0).is(Items.BEEF) && feeder.getItem(0).getCount() == 4, "meat must land in a meat slot");
        check(helper, feederStorage.extract(new ItemStack(Items.BEEF), 1, true).isEmpty(),
                "feeder faces must not extract food");

        BlockPos cratePos = new BlockPos(1, 1, 0);
        helper.setBlock(cratePos, BugRegistry.CRATE.get());
        var crate = (BugCrateBlockEntity) helper.getBlockEntity(cratePos);
        TestPlatform.ItemAccess crateStorage = sided(helper, cratePos);
        check(helper, crateStorage != null, "bug crate must expose item automation");
        check(helper, commit(crateStorage, new ItemStack(BugRegistry.CRICKETS.get(), 2)) == 2, "bug crate must accept insects");
        check(helper, commit(crateStorage, new ItemStack(Items.WHEAT_SEEDS, 3)) == 3, "bug crate must accept insect food");
        check(helper, crate.getItem(0).is(BugRegistry.CRICKETS.get()) && crate.getItem(3).is(Items.WHEAT_SEEDS),
                "insects and food must stay in their slots");

        BlockPos cultivatorPos = new BlockPos(2, 1, 0);
        helper.setBlock(cultivatorPos, CultivatorRegistry.CULTIVATOR.get());
        var cultivator = (CultivatorBlockEntity) helper.getBlockEntity(cultivatorPos);
        TestPlatform.ItemAccess cultivatorStorage = sided(helper, cultivatorPos);
        check(helper, cultivatorStorage != null, "cultivator must expose item automation");
        check(helper, commit(cultivatorStorage, new ItemStack(Items.WATER_BUCKET)) == 1, "cultivator must accept a water bucket");
        check(helper, cultivator.getItem(2).is(Items.WATER_BUCKET), "water bucket must land in the water slot");

        BlockPos calcifierPos = new BlockPos(3, 1, 0);
        helper.setBlock(calcifierPos, HatcheryRegistry.block(HatcheryRegistry.Machine.CALCIFIER));
        var calcifier = (HatcheryBlockEntity) helper.getBlockEntity(calcifierPos);
        TestPlatform.ItemAccess calcifierStorage = sided(helper, calcifierPos);
        check(helper, calcifierStorage != null, "calcifier must expose item automation");
        check(helper, commit(calcifierStorage, new ItemStack(Items.EGG)) == 1, "calcifier must accept a chicken egg");
        check(helper, calcifier.getItem(1).is(Items.EGG), "chicken egg must land in the egg slot");

        BlockPos extractorPos = new BlockPos(0, 1, 2);
        helper.setBlock(extractorPos, DnaMachineRegistry.EXTRACTOR.get());
        var extractor = (DnaMachineBlockEntity) helper.getBlockEntity(extractorPos);
        TestPlatform.ItemAccess extractorStorage = sided(helper, extractorPos);
        check(helper, extractorStorage != null, "extractor must expose item automation");
        check(helper, commit(extractorStorage, new ItemStack(GeneticsRegistry.STORAGE_DISC.get())) == 1,
                "extractor must accept a blank storage disc");
        check(helper, extractor.getItem(1).is(GeneticsRegistry.STORAGE_DISC.get()), "blank disc must land in the disc slot");

        BlockPos synthesizerPos = new BlockPos(1, 1, 2);
        helper.setBlock(synthesizerPos, DnaMachineRegistry.SYNTHESIZER.get());
        var synthesizer = (DnaMachineBlockEntity) helper.getBlockEntity(synthesizerPos);
        TestPlatform.ItemAccess synthesizerStorage = sided(helper, synthesizerPos);
        check(helper, synthesizerStorage != null, "synthesizer must expose item automation");
        check(helper, commit(synthesizerStorage, new ItemStack(ComponentsRegistry.EMPTY_TEST_TUBE.get())) == 1,
                "synthesizer must accept an empty test tube");
        check(helper, commit(synthesizerStorage, new ItemStack(ComponentsRegistry.DNA_NUCLEOTIDES.get(), 2)) == 2,
                "synthesizer must accept nucleotides");
        check(helper, synthesizer.getItem(1).is(ComponentsRegistry.EMPTY_TEST_TUBE.get())
                        && synthesizer.getItem(2).is(ComponentsRegistry.DNA_NUCLEOTIDES.get())
                        && synthesizer.getItem(2).getCount() == 2,
                "test tube and nucleotides must land in their slots");
        helper.succeed();
    }
}
