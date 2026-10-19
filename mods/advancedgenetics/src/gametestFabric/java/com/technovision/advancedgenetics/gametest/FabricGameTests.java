package com.technovision.advancedgenetics.gametest;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.fabric.GeneticsHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.genetics;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.player;

/** Fabric-only save compatibility with upstream's Cardinal Components player data. */
public class FabricGameTests {

    @GameTest(template = TEMPLATE)
    public static void upstreamCardinalComponentsSavesLoad(GameTestHelper helper) {
        ServerPlayer upstream = player(helper, "ag_upstream");
        CompoundTag saved = upstream.saveWithoutId(new CompoundTag());
        saved.remove(GeneticsHolder.SAVE_KEY);
        saved.put("cardinal_components", components(Genes.FLIGHT));

        ServerPlayer restored = player(helper, "ag_upstream_load");
        restored.load(saved);
        check(genetics(restored).geneCount() == 1 && genetics(restored).hasGene(Genes.FLIGHT),
                "Upstream Cardinal Components genes did not load: " + genetics(restored).getGenes());
        CompoundTag resaved = restored.saveWithoutId(new CompoundTag());
        check(resaved.getCompound(GeneticsHolder.SAVE_KEY).getCompound("genes").contains(Genes.FLIGHT.toString()),
                "Loaded upstream genes were not saved under " + GeneticsHolder.SAVE_KEY);

        ServerPlayer current = player(helper, "ag_current", Genes.SPEED);
        CompoundTag both = current.saveWithoutId(new CompoundTag());
        both.put("cardinal_components", components(Genes.FLIGHT));
        ServerPlayer preferred = player(helper, "ag_current_load");
        preferred.load(both);
        check(genetics(preferred).hasGene(Genes.SPEED) && !genetics(preferred).hasGene(Genes.FLIGHT),
                "The legacy compound overrode current genetics: " + genetics(preferred).getGenes());
        helper.succeed();
    }

    private static CompoundTag components(Genes gene) {
        CompoundTag genes = new CompoundTag();
        genes.putString(gene.toString(), gene.getName());
        CompoundTag component = new CompoundTag();
        component.put("genes", genes);
        CompoundTag components = new CompoundTag();
        components.put(GeneticsHolder.SAVE_KEY, component);
        return components;
    }
}
