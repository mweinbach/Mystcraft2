package ports.jurassicraft.village;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import ports.jurassicraft.PortMod;

/** Append to vanilla pools without replacing datapacks or other mods' entries. */
@Mod.EventBusSubscriber(modid = PortMod.MOD_ID)
public final class VillagePools {
    private static final Field TEMPLATES = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210560_");
    private static final Field MAX_SIZE = ObfuscationReflectionHelper.findField(StructureTemplatePool.class, "f_210562_");
    private static final Set<StructureTemplatePool> MODIFIED = Collections.newSetFromMap(new WeakHashMap<>());
    private VillagePools() {}

    @SubscribeEvent public static void addVillageBuildings(ServerAboutToStartEvent event) {
        var pools = event.getServer().registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        var processorLists = event.getServer().registryAccess().registryOrThrow(Registry.PROCESSOR_LIST_REGISTRY);
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
    @SuppressWarnings("unchecked") private static void inject(StructureTemplatePool pool, String biome, String variant, StructureProcessorList processorList) {
        if (!MODIFIED.add(pool)) return;
        try {
            ObjectArrayList<StructurePoolElement> elements = (ObjectArrayList<StructurePoolElement>) TEMPLATES.get(pool);
            Holder<StructureProcessorList> processors = Holder.direct(processorList);
            // Jigsaw pools have no per-village counter, so the legacy 0–1 cap per building category cannot be preserved here.
            for (String id : List.of("geneticist_house", "fossildigsite_1", "fossildigsite_2", "fossildigsite_3", "fossildigsite_3")) {
                elements.add(StructurePoolElement.single(PortMod.MOD_ID + ":village/" + biome + "/" + variant + id, processors).apply(StructureTemplatePool.Projection.RIGID));
            }
            MAX_SIZE.setInt(pool, Integer.MIN_VALUE);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not add JurassiCraft village buildings", e);
        }
    }
}
