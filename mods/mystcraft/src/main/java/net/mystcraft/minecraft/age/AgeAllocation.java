package net.mystcraft.minecraft.age;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.MinecraftServer;
import net.mystcraft.minecraft.age.generation.ClassicGenerationCompiler;
import net.mystcraft.minecraft.age.generation.ClassicGenerationPlan;
import net.mystcraft.minecraft.age.generation.FrozenAgePlan;
import net.mystcraft.minecraft.age.grammar.ClassicGrammar;
import net.mystcraft.minecraft.age.visual.AgeVisualCompiler;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;
import net.mystcraft.minecraft.writing.PageData;

/** Fully resolved before reserving an identity; authored pages remain separate from generated execution. */
public final class AgeAllocation {
    public static final String EXECUTION = "ClassicExecution";
    private AgeAllocation() {}
    public record Prepared(AgeSymbolCompiler.Compilation compilation, CompoundTag state) {
        public Prepared { state = state.copy(); }
        @Override public CompoundTag state() { return state.copy(); }
    }
    public static Prepared compile(MinecraftServer server, List<CompoundTag> originalPages, long seed) {
        var biomes = server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var result = AgeSymbolCompiler.compile(originalPages, seed, ClassicGrammar.forRegistry(biomes));
        if (!result.successful()) return new Prepared(result, new CompoundTag());
        var execution = result.executionPages();
        var generation = ClassicGenerationCompiler.compileExecution(execution, seed).plan().resolveBiomes(biomes, seed);
        // Validate material and biome references before a failed book can reserve an Age number.
        generation.fill(); generation.sea(); generation.biomeSource(biomes, seed);
        CompoundTag state = new CompoundTag();
        state.putInt("ClassicRuntimeVersion", 1);
        state.put(FrozenAgePlan.KEY, FrozenAgePlan.write(result.plan()));
        state.put(ClassicGenerationPlan.KEY, generation.toTag());
        state.putInt("GeneratorVersion", generation.terrain().version());
        state.putString("GeneratorBackend", "classic_v" + generation.terrain().version());
        state.put("GeneratorSpec", generation.terrain().toTag());
        var visuals = AgeVisualCompiler.parseExecution(execution, seed);
        state.put(AgeVisualSettings.STATE_KEY, visuals.settings().toTag());
        ListTag lightning = new ListTag();
        visuals.lightningGradients().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(gradient -> {
            int index = gradient.getKey();
            var stops = gradient.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putLong("Seed", result.symbolSeeds().get(index));
            entry.put("Stops", AgeVisualSettings.writeGradient(stops));
            lightning.add(entry);
        });
        state.put("ExplicitLightning", lightning);
        state.putBoolean("MystcraftPvp", execution.stream().noneMatch(page ->
                PageData.symbol(page).map(id -> id.toString().equals("mystcraft:pvpoff")).orElse(false)));
        state.putInt("SymbolInstability", result.symbolInstability());
        CompoundTag effects = new CompoundTag(); result.explicitEffects().forEach(effects::putInt);
        state.put("ExplicitEffects", effects);
        CompoundTag frozen = new CompoundTag(); frozen.putInt("Version", result.version());
        PageData.writePages(frozen, execution);
        ListTag seeds = new ListTag(); result.symbolSeeds().forEach(value -> seeds.add(LongTag.valueOf(value)));
        frozen.put("SymbolSeeds", seeds); state.put(EXECUTION, frozen);
        return new Prepared(result, state);
    }
}
