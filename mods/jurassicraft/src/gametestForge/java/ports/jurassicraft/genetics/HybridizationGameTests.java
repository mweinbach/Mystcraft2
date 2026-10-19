package ports.jurassicraft.genetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.plants.PlantSpecies;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HybridizationGameTests {
    private HybridizationGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void hybridMatchingRejectsIncompleteOrExtraSamples(GameTestHelper helper) {
        // A test-only recipe exercises matching without adding a fictional hybrid to the game.
        var recipe = new Hybridization.Recipe(Species.TRICERATOPS, List.of(Species.VELOCIRAPTOR, Species.DILOPHOSAURUS));
        var recipes = List.of(recipe);
        List<ItemStack> discs = new ArrayList<>(Collections.nCopies(8, ItemStack.EMPTY));
        DNAData first = DNAData.dinosaur(Species.DILOPHOSAURUS, 100, "ACGTACGTAC");
        discs.set(1, Genetics.disc(null, first));
        discs.get(1).getTag().putString("OtherModData", "preserved");
        discs.set(5, Genetics.disc(null, DNAData.dinosaur(Species.VELOCIRAPTOR, 100, "TTTTGGGGCC")));
        ItemStack result = Hybridization.hybridize(discs, recipes);
        require(helper, Genetics.read(result.getTag()).filter(dna -> dna.dinosaur() == Species.TRICERATOPS
                && dna.quality() == 100 && dna.genetics().equals(first.genetics())).isPresent(), "matched parents must retain the first genome");
        require(helper, result.getTag().getString("OtherModData").equals("preserved") && discs.get(1).getCount() == 1,
                "hybridization must preserve metadata without mutating inputs");
        require(helper, Hybridization.hybridize(discs).isEmpty() && !Hybridization.hasRecipes(), "no fictional hybrid recipe may be registered");
        discs.set(7, discs.get(5).copy());
        require(helper, Hybridization.hybridize(discs, recipes).isEmpty(), "duplicate parents must be rejected");
        discs.set(7, Genetics.disc(null, DNAData.dinosaur(Species.GALLIMIMUS, 100, "ACGTACGTAC")));
        require(helper, Hybridization.hybridize(discs, recipes).isEmpty(), "extra parents must be rejected");
        discs.set(7, ItemStack.EMPTY);
        discs.set(5, Genetics.disc(null, DNAData.dinosaur(Species.VELOCIRAPTOR, 95, "TTTTGGGGCC")));
        require(helper, Hybridization.hybridize(discs, recipes).isEmpty(), "incomplete DNA must be rejected");
        discs.set(5, Genetics.disc(null, DNAData.plant(PlantSpecies.CALAMITES, 100)));
        require(helper, Hybridization.hybridize(discs, recipes).isEmpty(), "plant DNA must be rejected");
        helper.succeed();
    }
}
