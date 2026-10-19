package ports.jurassicraft.jei;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.SkeletonDisplayItem;
import ports.jurassicraft.assembly.SkeletonRecipes;
import ports.jurassicraft.bugs.BugFoods;
import ports.jurassicraft.cultivator.CultivatorBlockEntity;
import ports.jurassicraft.cultivator.CultivatorNutrition;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.figure.ActionFigureCycleRecipe;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.grinder.FossilGrinderRecipes;
import ports.jurassicraft.hatching.HatcheryRecipes;

/** The preview catalog is deliberately usable by a server without JEI installed. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipePreviewGameTests {
    private RecipePreviewGameTests() {}
    private static void require(GameTestHelper helper, boolean ok, String message) { if (!ok) helper.fail(message); }
    private static boolean contains(List<ItemStack> options, ItemStack expected) {
        return options.stream().anyMatch(stack -> ItemStack.isSameItemSameTags(stack, expected) && stack.getCount() == expected.getCount());
    }
    private static boolean hasDna(List<ItemStack> options, ItemStack expected) {
        return options.stream().anyMatch(stack -> stack.is(expected.getItem()) && RecipePreviews.dnaSubtype(stack).equals(RecipePreviews.dnaSubtype(expected)));
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void previewsMatchMachineProcessing(GameTestHelper helper) {
        var catalog = RecipePreviews.build();
        for (var kind : RecipePreviews.Kind.values()) {
            require(helper, !catalog.get(kind).isEmpty(), "empty recipe category " + kind);
            for (var preview : catalog.get(kind)) {
                for (var options : preview.outputs()) for (var result : options)
                    require(helper, !result.isEmpty(), "empty preview result in " + kind);
                ItemStack first = preview.inputs().stream().filter(options -> !options.isEmpty())
                        .findFirst().orElseThrow().get(0);
                List<ItemStack> outputs = preview.outputs().get(0);
                switch (kind) {
                    case GRINDING -> {
                        require(helper, FossilGrinderRecipes.accepts(first), "grinder preview input is not accepted");
                        for (int seed = 0; seed < 24; seed++) {
                            ItemStack result = FossilGrinderRecipes.grind(first, RandomSource.create(seed));
                            require(helper, contains(outputs, result), "grinder output missing from preview for " + first);
                        }
                    }
                    case EXTRACTING -> {
                        for (int seed = 0; seed < 24; seed++) {
                            ItemStack result = Genetics.extract(first, RandomSource.create(seed));
                            require(helper, !result.isEmpty() && hasDna(outputs, result), "extractor output missing from preview for " + first);
                        }
                    }
                    case SEQUENCING -> {
                        for (int seed = 0; seed < 24; seed++) {
                            ItemStack result = Genetics.sequence(first, RandomSource.create(seed));
                            require(helper, !result.isEmpty() && hasDna(outputs, result), "sequencer output missing from preview for " + first);
                        }
                    }
                    case COMBINING -> {
                        for (ItemStack second : preview.inputs().get(1)) {
                            ItemStack result = Genetics.combine(first, second);
                            require(helper, hasDna(outputs, result), "combination output missing from preview");
                        }
                    }
                    case SYNTHESIZING -> require(helper, contains(outputs, Genetics.synthesize(first)), "synthesizer preview differs from machine");
                    case EMBRYONIC -> require(helper, contains(outputs, HatcheryRecipes.embryonic(first,
                            preview.inputs().get(1).get(0), preview.inputs().get(2).get(0))), "embryonic preview differs from machine");
                    case CALCIFYING -> require(helper, contains(outputs, HatcheryRecipes.calcify(first, preview.inputs().get(1).get(0))), "calcifier preview differs from machine");
                    case INCUBATING -> {
                        require(helper, contains(outputs, HatcheryRecipes.incubate(first, 0)), "missing female incubator output");
                        require(helper, contains(outputs, HatcheryRecipes.incubate(first, 100)), "missing male incubator output");
                    }
                    case CULTIVATING -> {
                        require(helper, CultivatorBlockEntity.validSyringe(first), "cultivator preview requires a valid syringe");
                        require(helper, CultivatorBlockEntity.accepts(2, preview.inputs().get(1).get(0)), "cultivator preview requires water");
                        for (ItemStack food : preview.inputs().get(2))
                            require(helper, CultivatorNutrition.get(food) != null, "cultivator preview includes unusable food");
                        require(helper, outputs.size() == 2 && outputs.stream().allMatch(output ->
                                Genetics.read(output.getTag()).equals(Genetics.read(first.getTag()))), "cultivator must retain the syringe DNA");
                        require(helper, !outputs.get(0).getTag().getBoolean("Gender") && outputs.get(1).getTag().getBoolean("Gender"),
                                "cultivator preview must show both temperature outcomes");
                    }
                    case BREEDING -> require(helper, BugFoods.offspring(first, preview.inputs().get(1).get(0)) == outputs.get(0).getCount(), "incorrect insect output");
                    case SKELETON -> {
                        SimpleContainer grid = new SimpleContainer(25);
                        for (int i = 0; i < 25; i++) if (!preview.inputs().get(i).isEmpty()) grid.setItem(i, preview.inputs().get(i).get(0));
                        var match = SkeletonRecipes.find(grid);
                        require(helper, match != null && match.species() == SkeletonDisplayItem.species(outputs.get(0))
                                && match.fresh() == SkeletonDisplayItem.fresh(outputs.get(0)), "skeleton preview grid does not match its output");
                    }
                    default -> { }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void discSubtypesTrackSpeciesAndQuality(GameTestHelper helper) {
        var catalog = RecipePreviews.build();
        var first = catalog.get(RecipePreviews.Kind.COMBINING).get(0).inputs().get(0);
        ItemStack five = first.get(0), ten = first.get(1), otherGenes = five.copy();
        DNAData dna = Genetics.read(otherGenes.getTag()).orElseThrow();
        if (dna.kind() == DNAData.Kind.DINOSAUR) {
            Genetics.write(otherGenes.getTag(), DNAData.dinosaur(dna.dinosaur(), dna.quality(), "TTTTTTTTTT"));
            require(helper, RecipePreviews.dnaSubtype(five).equals(RecipePreviews.dnaSubtype(otherGenes)), "genes alone must not split recipe eligibility");
        }
        require(helper, !RecipePreviews.dnaSubtype(five).equals(RecipePreviews.dnaSubtype(ten)), "quality changes disc lookup");
        ItemStack otherSpecies = catalog.get(RecipePreviews.Kind.COMBINING).get(1).inputs().get(0).get(0);
        require(helper, !RecipePreviews.dnaSubtype(five).equals(RecipePreviews.dnaSubtype(otherSpecies)), "species changes disc lookup");
        helper.succeed();
    }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void figurePreviewsMatchCraftingCycle(GameTestHelper helper) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public void slotsChanged(Container inventory) { }
        };
        CraftingContainer grid = new CraftingContainer(menu, 1, 1);
        var cycle = new ActionFigureCycleRecipe(new ResourceLocation(PortMod.MOD_ID, "test_figure_preview"));
        java.util.Set<String> subtypes = new java.util.HashSet<>();
        for (var preview : RecipePreviews.figureCycles()) {
            subtypes.add(RecipePreviews.figureSubtype(preview.input()));
            ItemStack source = preview.input().copy();
            source.getOrCreateTagElement("BlockEntityTag").putInt("Gender", 2);
            grid.setItem(0, source);
            require(helper, cycle.matches(grid, helper.getLevel()), "figure preview must be a valid cycle input");
            ItemStack actual = cycle.assemble(grid);
            require(helper, RecipePreviews.figureSubtype(actual).equals(RecipePreviews.figureSubtype(preview.output())), "figure preview output differs from crafting");
            require(helper, actual.getTag().getCompound("BlockEntityTag").getInt("Gender") == 2, "cycle must preserve other figure data");
        }
        require(helper, subtypes.size() == RecipePreviews.figureCycles().size(), "JEI figure subtypes must distinguish every species");
        require(helper, RecipePreviews.figureSubtype(new ItemStack(ActionFigureRegistry.ITEM.get())).equals("brachiosaurus"), "untagged starter figure uses the default subtype");
        helper.succeed();
    }
}
