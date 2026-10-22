package ports.jurassicraft.loot;

import com.google.gson.JsonParser;
import java.io.IOException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.journal.JournalRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** The cryonics marker has no upstream table; the chest must keep resolving to the laboratory. */
public final class CryonicsLootChecks {
    private CryonicsLootChecks() {}

    public static void cryonicsResolvesToLaboratory(GameTestHelper helper) {
        ResourceLocation laboratory = new ResourceLocation(PortMod.MOD_ID, "structure/visitor_centre/laboratory");
        ResourceLocation cryonics = new ResourceLocation(PortMod.MOD_ID, "structure/visitor_centre/cryonics");
        var json = new ResourceLocation(PortMod.MOD_ID, "loot_tables/structure/visitor_centre/cryonics.json");
        var resource = helper.getLevel().getServer().getResourceManager().getResource(json);
        if (resource.isEmpty()) helper.fail("cryonics loot json missing");
        try (var reader = resource.get().openAsReader()) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var pools = root.getAsJsonArray("pools");
            if (pools.size() != 1) helper.fail("cryonics table must contain one pool");
            var entries = pools.get(0).getAsJsonObject().getAsJsonArray("entries");
            if (entries.size() != 1) helper.fail("cryonics pool must contain one entry");
            var entry = entries.get(0).getAsJsonObject();
            if (!"minecraft:loot_table".equals(entry.get("type").getAsString()) || !laboratory.toString().equals(entry.get("name").getAsString()))
                helper.fail("cryonics pool must reference the laboratory table");
        } catch (IOException failure) {
            helper.fail("could not read cryonics loot json: " + failure.getMessage());
        }
        var table = helper.getLevel().getServer().getLootTables().get(cryonics);
        var context = new LootContext.Builder(helper.getLevel()).withRandom(RandomSource.create(419))
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        boolean dna = false, tissue = false, amber = false, laboratoryItem = false, any = false;
        for (int i = 0; i < 600; i++) for (ItemStack stack : table.getRandomItems(context)) {
            if (stack.isEmpty()) continue;
            any = true;
            if (stack.is(JurassicContent.AMBER.get()) || stack.is(JurassicContent.AMBER_APHID.get())) amber = true;
            if (stack.is(GeneticsRegistry.STORAGE_DISC.get()) || stack.is(JournalRegistry.JOURNAL.get())
                    || stack.is(ComponentsRegistry.EMPTY_TEST_TUBE.get()) || stack.is(ComponentsRegistry.LIQUID_AGAR.get())
                    || stack.is(ComponentsRegistry.EMPTY_SYRINGE.get()) || stack.is(ComponentsRegistry.PETRI_DISH.get())
                    || stack.is(ComponentsRegistry.DNA_NUCLEOTIDES.get())) laboratoryItem = true;
            for (Species species : Species.values()) {
                if (stack.is(GeneticsRegistry.dinosaurDna(species).get())) dna = true;
                if (stack.is(GeneticsRegistry.softTissue(species).get())) tissue = true;
            }
            if (!tissue) for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric()
                    && stack.is(GeneticsRegistry.plantTissue(species).get())) tissue = true;
        }
        if (!any) helper.fail("cryonics loot produced nothing; the laboratory reference did not resolve");
        if (!dna || !tissue || !amber || !laboratoryItem)
            helper.fail("cryonics samples must include laboratory DNA, tissue, amber and base supplies");
    }
}
