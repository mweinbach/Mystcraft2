package ports.jurassicraft.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Server-side integration checks for the DNA machine workflow. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DnaMachineGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private DnaMachineGameTests() {}
    private static DnaMachineBlockEntity place(GameTestHelper helper, DnaMachineKind kind) {
        helper.setBlock(POS, switch (kind) {
            case EXTRACTOR -> DnaMachineRegistry.EXTRACTOR.get();
            case SEQUENCER -> DnaMachineRegistry.SEQUENCER.get();
            case COMBINATOR -> DnaMachineRegistry.COMBINATOR.get();
            case SYNTHESIZER -> DnaMachineRegistry.SYNTHESIZER.get();
        });
        return (DnaMachineBlockEntity) helper.getBlockEntity(POS);
    }
    private static void tick(GameTestHelper helper, DnaMachineBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) DnaMachineBlockEntity.tick(helper.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }
    private static ItemStack sample(DNAData dna) {
        ItemStack result = new ItemStack(GeneticsRegistry.softTissue(dna.dinosaur()).get());
        Genetics.write(result.getOrCreateTag(), dna);
        result.getOrCreateTag().putString("OtherModData", "preserve");
        return result;
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void sequencerPersistsAndKeepsGenome(GameTestHelper helper) {
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.SEQUENCER);
        DNAData one = DNAData.dinosaur(Species.VELOCIRAPTOR, 45, "ACGTACGTAC");
        DNAData two = DNAData.dinosaur(Species.TRICERATOPS, 75, "TTGGCCAATT");
        machine.setItem(0, sample(one)); machine.setItem(1, new ItemStack(GeneticsRegistry.STORAGE_DISC.get()));
        machine.setItem(2, sample(two)); machine.setItem(3, new ItemStack(GeneticsRegistry.STORAGE_DISC.get()));
        tick(helper, machine, 900);
        CompoundTag saved = machine.saveWithoutMetadata();
        DnaMachineBlockEntity restored = new DnaMachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.load(saved);
        tick(helper, restored, 1100);
        require(helper, Genetics.read(restored.getItem(6).getTag()).filter(one::equals).isPresent(), "first lane must preserve its genome");
        require(helper, Genetics.read(restored.getItem(7).getTag()).filter(two::equals).isPresent(), "second lane must complete independently");
        require(helper, restored.getItem(6).getTag().getString("OtherModData").equals("preserve"), "sample metadata must survive sequencing");
        require(helper, restored.getItem(0).isEmpty() && restored.getItem(1).isEmpty(), "sequencer must consume sample and disc");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void sequencerSplitsGuiInputsAndProtectsOccupiedAutomationSlots(GameTestHelper helper) {
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.SEQUENCER);
        var player = helper.makeMockPlayer();
        BlockPos world = helper.absolutePos(POS);
        player.setPos(world.getX() + .5, world.getY() + .5, world.getZ() + .5);
        var menu = (DnaMachineMenu) machine.createMenu(1, player.getInventory(), player);
        ItemStack tissue = sample(DNAData.dinosaur(Species.VELOCIRAPTOR, 50, "ACGTACGTAC"));
        ItemStack tissues = tissue.copy(); tissues.setCount(3);
        player.getInventory().setItem(9, new ItemStack(GeneticsRegistry.STORAGE_DISC.get(), 3));
        player.getInventory().setItem(10, tissues);
        menu.quickMoveStack(player, 9);
        menu.quickMoveStack(player, 10);
        for (int i = 0; i < 9; i++) {
            require(helper, menu.slots.get(i).getMaxStackSize() == 1, "sequencer menu slots must hold one item");
            if (i < 6) require(helper, machine.getItem(i).getCount() == 1, "shift-click must fill the three separate lanes");
        }
        var input = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        require(helper, input.insertItem(0, tissue.copy(), false).getCount() == 1 && machine.getItem(0).getCount() == 1,
                "automation must not add to an occupied sequencer lane");
        machine.setItem(0, ItemStack.EMPTY);
        ItemStack automated = tissue.copy(); automated.setCount(2);
        require(helper, input.insertItem(0, automated, false).isEmpty() && machine.getItem(0).getCount() == 2,
                "bulk automation into an empty lane must retain the original inventory behavior");
        DnaMachineBlockEntity restored = new DnaMachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.load(machine.saveWithoutMetadata());
        require(helper, restored.getItem(0).getCount() == 2, "preexisting larger stacks must survive reload");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void combineAndSynthesize(GameTestHelper helper) {
        DNAData first = DNAData.dinosaur(Species.TRICERATOPS, 55, "ACGTACGTAC");
        DNAData second = DNAData.dinosaur(Species.TRICERATOPS, 60, "TTTTGGGGCC");
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.COMBINATOR);
        machine.setItem(0, Genetics.disc(null, first));
        machine.setItem(1, Genetics.disc(null, DNAData.dinosaur(Species.VELOCIRAPTOR, 60, "TTTTGGGGCC")));
        tick(helper, machine, 1000);
        require(helper, machine.getItem(2).isEmpty() && !machine.getItem(0).isEmpty(), "different species cannot combine");
        machine.setItem(1, Genetics.disc(null, second));
        tick(helper, machine, 1000);
        ItemStack complete = machine.getItem(2).copy();
        require(helper, Genetics.read(complete.getTag()).filter(first.withQuality(100)::equals).isPresent(), "combination must cap quality and preserve first genome");
        machine = place(helper, DnaMachineKind.SYNTHESIZER);
        machine.setItem(0, complete.copy());
        machine.setItem(1, new ItemStack(ComponentsRegistry.EMPTY_TEST_TUBE.get()));
        machine.setItem(2, new ItemStack(ComponentsRegistry.DNA_NUCLEOTIDES.get()));
        tick(helper, machine, 1000);
        require(helper, machine.getItem(3).is(GeneticsRegistry.dinosaurDna(Species.TRICERATOPS).get()), "synthesizer must produce the matching DNA tube");
        require(helper, Genetics.read(machine.getItem(3).getTag()).filter(first.withQuality(100)::equals).isPresent(), "synthesizer must preserve genome");
        require(helper, !machine.getItem(0).isEmpty() && machine.getItem(1).isEmpty() && machine.getItem(2).isEmpty(), "disc is reusable but materials are consumed");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void synthesizerStacksPlantDnaWithoutMixingMetadata(GameTestHelper helper) {
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.SYNTHESIZER);
        DNAData plant = DNAData.plant(PlantSpecies.GINKGO, 100);
        ItemStack disc = Genetics.disc(null, plant);
        disc.getOrCreateTag().putString("OtherModData", "preserve");
        ItemStack tube = Genetics.synthesize(disc);
        require(helper, tube.getMaxStackSize() == 64 && GeneticsRegistry.dinosaurDna(Species.TRICERATOPS).get().getMaxStackSize() == 1,
                "plant tubes stack like upstream; dinosaur tubes remain unstackable");
        ItemStack nearlyFull = tube.copy(); nearlyFull.setCount(63);
        machine.setItem(0, disc.copy());
        machine.setItem(1, new ItemStack(ComponentsRegistry.EMPTY_TEST_TUBE.get(), 2));
        machine.setItem(2, new ItemStack(ComponentsRegistry.DNA_NUCLEOTIDES.get(), 2));
        machine.setItem(3, nearlyFull.copy());
        for (int i = 4; i < 7; i++) machine.setItem(i, new ItemStack(Items.STONE, 64));
        tick(helper, machine, 1000);
        require(helper, machine.getItem(3).getCount() == 64 && ItemStack.isSameItemSameTags(machine.getItem(3), tube)
                && machine.getItem(1).getCount() == 1 && machine.getItem(2).getCount() == 1,
                "compatible plant DNA must stack when no output slot is empty");
        tick(helper, machine, 1000);
        require(helper, machine.getItem(1).getCount() == 1 && machine.getItem(2).getCount() == 1,
                "full outputs must not consume synthesis materials");
        ItemStack different = nearlyFull.copy();
        different.getOrCreateTag().putString("OtherModData", "different");
        machine.setItem(3, different);
        tick(helper, machine, 1000);
        require(helper, machine.getItem(3).getCount() == 63 && machine.getItem(1).getCount() == 1,
                "different metadata must not be merged or consume materials");
        machine.setItem(4, nearlyFull.copy());
        tick(helper, machine, 1000);
        require(helper, machine.getItem(3).getCount() == 63 && machine.getItem(4).getCount() == 64
                && machine.getItem(1).isEmpty() && machine.getItem(2).isEmpty(),
                "the next compatible stack must receive the result without changing the other sample");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void extractionBlockedOutputAndAutomation(GameTestHelper helper) {
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.EXTRACTOR);
        var input = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        var output = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
        DNAData dna = DNAData.dinosaur(Species.VELOCIRAPTOR, 35, "AACCGGTTAC");
        ItemStack amber = new ItemStack(JurassicContent.AMBER.get());
        Genetics.write(amber.getOrCreateTag(), dna);
        require(helper, input.insertItem(0, amber, false).isEmpty(), "hopper must accept amber");
        require(helper, input.insertItem(1, new ItemStack(GeneticsRegistry.STORAGE_DISC.get()), false).isEmpty(), "hopper must accept a blank disc");
        require(helper, !output.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "hopper must not insert into output");
        for (int i = 2; i < 6; i++) machine.setItem(i, new ItemStack(Items.STONE));
        tick(helper, machine, 2000);
        require(helper, !machine.getItem(0).isEmpty() && !machine.getItem(1).isEmpty(), "blocked output must not consume inputs");
        machine.setItem(2, ItemStack.EMPTY);
        tick(helper, machine, 2000);
        require(helper, Genetics.read(output.extractItem(0, 1, false).getTag()).filter(dna::equals).isPresent(), "extraction must preserve tagged amber genome");
        ItemStack plant = Genetics.extract(new ItemStack(JurassicContent.AMBER_APHID.get()), RandomSource.create(15));
        require(helper, Genetics.read(plant.getTag()).filter(value -> value.kind() == DNAData.Kind.PLANT).isPresent(), "aphid amber must yield plant DNA");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void extractSpeciesMeat(GameTestHelper helper) {
        RandomSource random = RandomSource.create(19);
        for (Species species : Species.values()) {
            DNAData dna = DNAData.dinosaur(species, 70, "AACCGGTTAC");
            ItemStack sample = new ItemStack(DinosaurRegistry.rawMeat(species).get());
            require(helper, Genetics.extract(sample, random).isEmpty(), "untagged meat must not invent DNA");
            Genetics.write(sample.getOrCreateTag(), dna);
            sample.getTag().putString("OtherModData", "preserve");
            ItemStack disc = Genetics.extract(sample, random);
            require(helper, Genetics.read(disc.getTag()).filter(dna::equals).isPresent(), "species meat must preserve its DNA");
            require(helper, disc.getTag().getString("OtherModData").equals("preserve"), "species meat must preserve sample metadata");
            Species wrong = species == Species.VELOCIRAPTOR ? Species.TRICERATOPS : Species.VELOCIRAPTOR;
            Genetics.write(sample.getTag(), DNAData.dinosaur(wrong, 70, "AACCGGTTAC"));
            require(helper, Genetics.extract(sample, random).isEmpty(), "meat species and DNA identity must agree");
            ItemStack cooked = new ItemStack(DinosaurRegistry.cookedMeat(species).get());
            Genetics.write(cooked.getOrCreateTag(), dna);
            require(helper, Genetics.extract(cooked, random).isEmpty(), "cooked meat cannot be used for DNA extraction");
        }
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void displaySamplesSyncWithoutOtherInventory(GameTestHelper helper) {
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.SEQUENCER);
        DNAData dna = DNAData.dinosaur(Species.TRICERATOPS, 70, "AACCGGTTAC");
        machine.setItem(0, sample(dna));
        machine.setItem(1, new ItemStack(GeneticsRegistry.STORAGE_DISC.get()));
        machine.setItem(4, sample(dna));
        DnaMachineBlockEntity client = new DnaMachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        client.handleUpdateTag(machine.getUpdateTag());
        require(helper, Genetics.read(client.getItem(0).getTag()).filter(dna::equals).isPresent(), "first visible sample must sync with metadata");
        require(helper, Genetics.read(client.getItem(4).getTag()).filter(dna::equals).isPresent(), "third visible sample must sync");
        require(helper, client.getItem(1).isEmpty(), "non-visual inventory should not be sent to tracking clients");
        machine.removeItem(0, 1);
        client.handleUpdateTag(machine.getUpdateTag());
        require(helper, client.getItem(0).isEmpty(), "removed sample must disappear on clients");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void hybridModePreservesInventoryAndAutomation(GameTestHelper helper) {
        DnaMachineBlockEntity machine = place(helper, DnaMachineKind.COMBINATOR);
        DNAData first = DNAData.dinosaur(Species.TRICERATOPS, 50, "AACCGGTTAC");
        ItemStack normalA = Genetics.disc(null, first), normalB = Genetics.disc(null, first);
        ItemStack normalOutput = Genetics.disc(null, first.withQuality(100));
        machine.setItem(0, normalA.copy()); machine.setItem(1, normalB.copy());
        tick(helper, machine, 50);
        require(helper, machine.saveWithoutMetadata().getIntArray("Progress")[0] == 50, "combinator must advance before mode switching");
        machine.setItem(2, normalOutput.copy());
        var combinedCap = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        var automation = combinedCap.orElseThrow(IllegalStateException::new);
        // Unsided view: ten input-face slots (0, 1, 3-10) followed by the two outputs (2, 11).
        require(helper, automation.getSlots() == 12 && !automation.extractItem(10, 1, true).isEmpty()
                && automation.extractItem(0, 1, true).isEmpty(), "combined capability must only release the active output");
        var player = helper.makeMockPlayer();
        BlockPos world = helper.absolutePos(POS);
        player.setPos(world.getX() + .5, world.getY() + .5, world.getZ() + .5);
        var menu = (DnaMachineMenu) machine.createMenu(1, player.getInventory(), player);
        var otherMenu = (DnaMachineMenu) machine.createMenu(2, player.getInventory(), player);
        otherMenu.setCarried(normalA.copy());
        otherMenu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
        otherMenu.clicked(0, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        require(helper, !menu.clickMenuButton(player, 42), "unknown button IDs must be rejected");
        require(helper, menu.clickMenuButton(player, 0) && machine.hybridizerMode() && menu.hybridizerMode(), "menu button must switch and sync mode");
        CompoundTag visual = machine.getUpdateTag();
        require(helper, visual.getBoolean("VisualSync") && visual.getBoolean("HybridizerMode") && !visual.contains("Items"),
                "hybridizer mode must reach clients without the inventory");
        DnaMachineBlockEntity viewer = new DnaMachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        viewer.handleUpdateTag(visual);
        require(helper, viewer.hybridizerMode(), "client must use the hybridizer screens");
        otherMenu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
        require(helper, ItemStack.matches(otherMenu.getCarried(), normalA) && ItemStack.matches(machine.getItem(0), normalA),
                "a mode change in another menu must not complete a drag into hidden slots");
        otherMenu.setCarried(ItemStack.EMPTY);
        require(helper, combinedCap.isPresent() && automation.extractItem(10, 1, true).isEmpty(),
                "the stable automation view must follow the active mode");
        require(helper, !menu.slots.get(0).isActive() && menu.slots.get(3).isActive()
                && !menu.slots.get(0).mayPickup(player), "only current mode slots may be accessed");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(0, 0, ClickType.SWAP, player);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        player.getAbilities().instabuild = true;
        menu.clicked(0, 2, ClickType.CLONE, player);
        player.getAbilities().instabuild = false;
        require(helper, menu.getCarried().isEmpty() && ItemStack.matches(machine.getItem(0), normalA),
                "stale clicks, hotbar swaps, shift-clicks and creative clones must not access hidden slots");
        require(helper, machine.saveWithoutMetadata().getIntArray("Progress")[0] == 0, "mode change must reset progress");
        require(helper, ItemStack.matches(machine.getItem(0), normalA) && ItemStack.matches(machine.getItem(1), normalB)
                && ItemStack.matches(machine.getItem(2), normalOutput), "switching must preserve the old input and output slots");
        ItemStack full = Genetics.disc(null, DNAData.dinosaur(Species.VELOCIRAPTOR, 100, "TTTTGGGGCC"));
        require(helper, machine.canPlaceItem(3, full) && !machine.canPlaceItem(3, normalA)
                && !machine.canPlaceItem(0, full), "hybridizer inputs accept only complete dinosaur discs");
        machine.setItem(3, full.copy());
        tick(helper, machine, 1200);
        require(helper, machine.getItem(11).isEmpty() && ItemStack.matches(machine.getItem(3), full),
                "no registered hybrid must mean no result or consumed input");
        CompoundTag saved = machine.saveWithoutMetadata();
        DnaMachineBlockEntity restored = new DnaMachineBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.load(saved);
        require(helper, restored.hybridizerMode() && ItemStack.matches(restored.getItem(0), normalA)
                && ItemStack.matches(restored.getItem(2), normalOutput) && ItemStack.matches(restored.getItem(3), full),
                "both inventories and mode must survive reload");
        require(helper, menu.clickMenuButton(player, 0) && !machine.hybridizerMode()
                && ItemStack.matches(machine.getItem(3), full) && ItemStack.matches(machine.getItem(2), normalOutput),
                "switching back must not mix or remove either inventory");
        viewer.handleUpdateTag(machine.getUpdateTag());
        require(helper, !viewer.hybridizerMode(), "client must restore the combinator screens");
        menu.clicked(3, 0, ClickType.PICKUP, player);
        require(helper, menu.getCarried().isEmpty() && ItemStack.matches(machine.getItem(3), full),
                "switching back must also protect the hidden hybridizer inventory");
        helper.succeed();
    }
}
