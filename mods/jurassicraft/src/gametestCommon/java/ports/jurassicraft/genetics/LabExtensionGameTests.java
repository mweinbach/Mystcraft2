package ports.jurassicraft.genetics;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.hatching.IncubatorEnvironment;
import ports.jurassicraft.machine.DnaMachineBlockEntity;
import ports.jurassicraft.machine.DnaMachineKind;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Integration checks with items and blocks shaped like another mod's extensions. */
public final class LabExtensionGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static SynthesisBlock synthesisBlock;
    private static EnvironmentBlock environmentBlock;
    private static Item tissueFixture, synthesisFixture, environmentItemFixture, environmentBlockFixture;
    private LabExtensionGameTests() {}
    private static void check(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    /**
     * ItemStacks require registered items, so each loader's GameTest setup registers these fixtures
     * (Forge from {@code RegisterEvent}, Fabric from its GameTest initializer) only in GameTest runs.
     * Blocks must be registered before {@link #registerItems}.
     */
    public static void registerBlocks(BiConsumer<ResourceLocation, Block> registry) {
        synthesisBlock = new SynthesisBlock(); environmentBlock = new EnvironmentBlock();
        registry.accept(id("gametest_synthesis"), synthesisBlock);
        registry.accept(id("gametest_environment"), environmentBlock);
    }

    public static void registerItems(BiConsumer<ResourceLocation, Item> registry) {
        tissueFixture = new TissueItem(); synthesisFixture = new BlockItem(synthesisBlock, new Item.Properties());
        environmentItemFixture = new EnvironmentItem(); environmentBlockFixture = new BlockItem(environmentBlock, new Item.Properties());
        registry.accept(id("gametest_tissue"), tissueFixture);
        registry.accept(id("gametest_synthesis"), synthesisFixture);
        registry.accept(id("gametest_environment_item"), environmentItemFixture);
        registry.accept(id("gametest_environment"), environmentBlockFixture);
    }
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }

    private static final class TissueItem extends Item implements Sequencable {
        TissueItem() { super(new Item.Properties()); }
        @Override public boolean isSequencable(ItemStack input) { return input.getOrCreateTag().getBoolean("Ready"); }
        @Override public ItemStack sequence(ItemStack input, RandomSource random) {
            input.getOrCreateTag().putBoolean("MutatedByExtension", true);
            return new ItemStack(Items.DIAMOND);
        }
    }

    private static final class SynthesisBlock extends Block implements Synthesizable {
        RandomSource lastRandom;
        SynthesisBlock() { super(BlockBehaviour.Properties.of(Material.STONE)); }
        @Override public boolean isSynthesizable(ItemStack input) { return input.getTag().getInt("DNAQuality") == 100; }
        @Override public ItemStack synthesize(ItemStack input, RandomSource random) {
            lastRandom = random;
            return new ItemStack(Items.EMERALD);
        }
    }

    private static final class EnvironmentItem extends Item implements IncubatorEnvironment {
        EnvironmentItem() { super(new Item.Properties()); }
    }
    private static final class EnvironmentBlock extends Block implements IncubatorEnvironment {
        EnvironmentBlock() { super(BlockBehaviour.Properties.of(Material.DIRT)); }
    }

    @GameTest(template = "jurassicraft:dna_machine_test")
    public static void customItemSequencesAndCustomBlockSynthesizes(GameTestHelper helper) {
        helper.setBlock(POS, DnaMachineRegistry.SEQUENCER.get());
        var sequencer = (DnaMachineBlockEntity) helper.getBlockEntity(POS);
        ItemStack tissue = new ItemStack(tissueFixture);
        check(helper, !sequencer.canPlaceItem(0, tissue), "extension eligibility must be respected");
        tissue.getOrCreateTag().putBoolean("Ready", true);
        check(helper, sequencer.canPlaceItem(0, tissue) && Genetics.sequence(tissue, RandomSource.create(0)).is(Items.DIAMOND)
                        && !tissue.getTag().contains("MutatedByExtension"),
                "previews must accept eligible samples without mutating them");
        sequencer.setItem(0, tissue); sequencer.setItem(1, new ItemStack(GeneticsRegistry.STORAGE_DISC.get()));
        for (int i = 0; i < DnaMachineKind.SEQUENCER.ticks; i++)
            DnaMachineBlockEntity.tick(helper.getLevel(), sequencer.getBlockPos(), sequencer.getBlockState(), sequencer);
        check(helper, sequencer.getItem(6).is(Items.DIAMOND) && sequencer.getItem(0).isEmpty() && sequencer.getItem(1).isEmpty(),
                "sequencer must consume the custom sample and blank disc and output the extension result");
        sequencer.clearContent();

        helper.setBlock(POS, DnaMachineRegistry.SYNTHESIZER.get());
        var synthesizer = (DnaMachineBlockEntity) helper.getBlockEntity(POS);
        ItemStack input = new ItemStack(synthesisFixture);
        check(helper, !synthesizer.canPlaceItem(0, input), "custom synthesis still requires a DNAQuality tag");
        input.getOrCreateTag().putInt("DNAQuality", 99);
        check(helper, !synthesizer.canPlaceItem(0, input), "custom synthesis eligibility must be respected");
        input.getTag().putInt("DNAQuality", 100);
        check(helper, synthesizer.canPlaceItem(0, input), "a block-provided synthesis input must be accepted");
        synthesizer.setItem(0, input); synthesizer.setItem(1, new ItemStack(ComponentsRegistry.EMPTY_TEST_TUBE.get()));
        synthesizer.setItem(2, new ItemStack(ComponentsRegistry.DNA_NUCLEOTIDES.get()));
        for (int i = 0; i < DnaMachineKind.SYNTHESIZER.ticks; i++)
            DnaMachineBlockEntity.tick(helper.getLevel(), synthesizer.getBlockPos(), synthesizer.getBlockState(), synthesizer);
        check(helper, synthesizer.getItem(3).is(Items.EMERALD) && !synthesizer.getItem(0).isEmpty()
                && synthesizer.getItem(1).isEmpty() && synthesizer.getItem(2).isEmpty(),
                "synthesis must keep the custom source and consume only the two reagents");
        check(helper, synthesisBlock.lastRandom == helper.getLevel().random, "the actual synthesis result must use the world random source");
        synthesizer.clearContent();
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:dna_machine_test")
    public static void customItemAndBlockEnvironmentsIncubate(GameTestHelper helper) {
        helper.setBlock(POS, HatcheryRegistry.block(HatcheryRegistry.Machine.INCUBATOR));
        var machine = (HatcheryBlockEntity) helper.getBlockEntity(POS);
        ItemStack egg = new ItemStack(HatcheryRegistry.egg(Species.VELOCIRAPTOR));
        Genetics.write(egg.getOrCreateTag(), DNAData.dinosaur(Species.VELOCIRAPTOR, 100, "ACGTACGTAC"));
        ItemStack[] environments = {new ItemStack(environmentItemFixture), new ItemStack(environmentBlockFixture)};
        for (int lane = 0; lane < environments.length; lane++) {
            ItemStack environment = environments[lane];
            check(helper, HatcheryRecipes.isEnvironment(environment) && machine.canPlaceItem(5, environment),
                    "custom item or block environment must be accepted");
            machine.setItem(lane, egg.copy()); machine.setItem(5, environment);
            for (int i = 0; i < HatcheryRegistry.Machine.INCUBATOR.ticks; i++)
                HatcheryBlockEntity.tick(helper.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
            check(helper, machine.getItem(lane).is(HatcheryRegistry.hatchedEgg(Species.VELOCIRAPTOR)) && machine.getItem(5).isEmpty(),
                    "custom environment must be consumed when incubation completes");
        }
        check(helper, HatcheryRecipes.isEnvironment(new ItemStack(HatcheryRegistry.PEAT_MOSS_ITEM.get())),
                "the built-in peat moss remains supported");
        machine.clearContent();
        helper.succeed();
    }
}
