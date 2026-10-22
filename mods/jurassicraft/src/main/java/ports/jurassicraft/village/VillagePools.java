package ports.jurassicraft.village;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import ports.jurassicraft.PortMod;

/** Append to vanilla pools without replacing datapacks or other mods' entries. */
public final class VillagePools {
    private static final Set<StructureTemplatePool> MODIFIED = Collections.newSetFromMap(new WeakHashMap<>());
    private VillagePools() {}

    public static void addVillageBuildings(MinecraftServer server) {
        var pools = server.registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        var processorLists = server.registryAccess().registryOrThrow(Registry.PROCESSOR_LIST_REGISTRY);
        for (String biome : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            StructureTemplatePool pool = pools.get(new ResourceLocation("minecraft", "village/" + biome + "/houses"));
            if (pool != null) inject(pool, biome);
            StructureTemplatePool abandoned = pools.get(new ResourceLocation("minecraft", "village/" + biome + "/zombie/houses"));
            StructureProcessorList decay = processorLists.get(new ResourceLocation("minecraft", "zombie_" + biome));
            if (abandoned != null && decay != null) injectAbandoned(abandoned, biome, decay);
        }
    }
    static void inject(StructureTemplatePool pool, String biome) {
        inject(pool, biome, "", new StructureProcessorList(List.of(new VillageProcessor())));
    }
    static void injectAbandoned(StructureTemplatePool pool, String biome, StructureProcessorList decay) {
        var processors = new ArrayList<>(decay.list());
        processors.add(new VillageProcessor());
        inject(pool, biome, "zombie/", new StructureProcessorList(processors));
    }
    private static void inject(StructureTemplatePool pool, String biome, String variant, StructureProcessorList processorList) {
        if (!MODIFIED.add(pool)) return;
        ObjectArrayList<StructurePoolElement> elements = pool.templates;
        Holder<StructureProcessorList> processors = Holder.direct(processorList);
        // fossildigsite_3 is listed twice so its weight stays about half of the digsites. All three share one cap.
        for (String id : List.of("geneticist_house", "fossildigsite_1", "fossildigsite_2", "fossildigsite_3", "fossildigsite_3")) {
            var element = StructurePoolElement.single(PortMod.MOD_ID + ":village/" + biome + "/" + variant + id, processors)
                    .apply(StructureTemplatePool.Projection.RIGID);
            var category = id.startsWith("fossildigsite") ? VillageLimits.Category.DIGSITE : VillageLimits.Category.GENETICIST;
            elements.add(new LimitedVillageElement(element, category));
        }
        // Recompute the cached jigsaw size with the new buildings.
        pool.maxSize = Integer.MIN_VALUE;
    }

    /** Replaces pool entries for a test and returns the previous list. */
    static List<StructurePoolElement> swapTemplates(StructureTemplatePool pool, List<StructurePoolElement> elements) {
        List<StructurePoolElement> saved = new ArrayList<>(pool.templates);
        pool.templates.clear();
        pool.templates.addAll(elements);
        pool.maxSize = Integer.MIN_VALUE;
        return saved;
    }

    static List<StructurePoolElement> limitedTemplates(StructureTemplatePool pool) {
        List<StructurePoolElement> found = new ArrayList<>();
        for (StructurePoolElement element : pool.templates) {
            if (element instanceof LimitedVillageElement) found.add(element);
        }
        return found;
    }
}
