package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.age.AgeSavedData;
import net.mystcraft.minecraft.age.AgeSymbolCompiler;
import net.mystcraft.minecraft.age.generation.ClassicChunkGenerator;
import net.mystcraft.minecraft.age.generation.ClassicTerrainSpec;
import net.mystcraft.minecraft.age.generation.FrozenAgePlan;
import net.mystcraft.minecraft.writing.PageData;

/** Test-only source terrain/persistence checks; creates no canonical Ages or world blocks. */
public final class TerrainSmoke {
    private TerrainSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        check(server.isSameThread(), "terrain smoke must use server thread");
        var passed = new ArrayList<String>();
        var structures = server.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        var biomes = server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var source = new FixedBiomeSource(biomes.getHolderOrThrow(Biomes.PLAINS));
        for (String mode : List.of("flat", "void", "nether", "end", "normal", "amplified")) {
            var plan = new AgeSymbolCompiler.Plan(mode, null, "normal", List.of());
            var spec = ClassicTerrainSpec.fromPlan(plan);
            check(spec.equals(ClassicTerrainSpec.fromTag(spec.toTag())), "terrain specification NBT roundtrip " + mode);
            var generator = new ClassicChunkGenerator(structures, source, 123456789L, spec);
            for (int[] position : new int[][] {{0, 0}, {-17, -33}, {16000, -22000}}) {
                var column = generator.column(position[0], position[1]);
                check(Arrays.equals(column, generator.column(position[0], position[1])), "repeat generation must be stable");
                check(column.length == 256, "classic logical height must be 256");
                if (mode.equals("flat")) {
                    check(column[0].is(Blocks.BEDROCK) && column[63].is(Blocks.STONE) && column[64].isAir(), "source flat ground64 floor0");
                } else if (mode.equals("void")) {
                    check(Arrays.stream(column).allMatch(state -> state.isAir()), "source void must contain no terrain");
                } else {
                    check(Arrays.stream(column, 128, 256).allMatch(state -> state.isAir()), "source density only fills lower128");
                    if (!mode.equals("end")) check(column[0].is(Blocks.BEDROCK), "source lower bedrock");
                    else check(Arrays.stream(column).noneMatch(state -> state.is(Blocks.BEDROCK)), "source End disables bedrock");
                }
            }
        }
        passed.add("Terrain: six classic modes deterministic columns, lower128 density bounds and source defaults");
        var saved = new AgeSavedData();
        for (String mode : List.of("flat", "void", "nether", "end", "normal", "amplified")) {
            CompoundTag page = new CompoundTag(); PageData.setLegacySymbol(page, "mystcraft:terrain" + mode);
            CompoundTag panel = PageData.payload(new ItemStack(MystcraftItems.LINKING_PANEL.get()));
            var pages = List.of(panel, page);
            check(AgeBookData.validPages(pages), "fixture must be a real panel-first descriptive book");
            var age = saved.allocate(0, "Terrain smoke " + mode, pages);
            check(age.state().getInt("GeneratorVersion") == ClassicTerrainSpec.fromPlan(new AgeSymbolCompiler.Plan(mode, null, "normal", List.of())).version(), "explicit backend version " + mode);
            check(FrozenAgePlan.read(age.state().getCompound(FrozenAgePlan.KEY)).terrain().equals(mode), "frozen compiled terrain " + mode);
        }
        var restored = AgeSavedData.load(((net.minecraft.world.level.saveddata.SavedData) saved).save(new CompoundTag()));
        for (var age : saved.entries()) check(restored.get(age.id()).state().equals(age.state()), "all frozen generation inputs survive saved-data roundtrip");
        passed.add("Terrain: flat/void/Nether/End freeze version1, normal/amplified version2; saved-data inputs roundtrip");
        return passed;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
