package net.mystcraft.smoke;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.mystcraft.minecraft.instability.InstabilityProfile;
import net.mystcraft.minecraft.instability.InstabilityWorldData;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;

/** Real registry fluid-factor coverage and persisted watcher migration without changing operator configuration. */
public final class InstabilityFluidSmoke {
    private InstabilityFluidSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        check(server.isSameThread(), "fluid factor audit must use server thread");
        var choices = DynamicSymbolPages.fluidChoices();
        check(!choices.isEmpty(), "Mystcraft black ink must provide at least one ordinary registered fluid");
        for (var choice : choices) {
            var fluid = Registry.FLUID.get(new net.minecraft.resources.ResourceLocation(choice.payload().getString("Fluid")));
            String block = Registry.BLOCK.getKey(fluid.defaultFluidState().createLegacyBlock().getBlock()).toString();
            var factors = InstabilityProfile.FACTORS.get(block);
            check(factors != null && Float.isFinite(factors.accessible()) && Float.isFinite(factors.total()), "Eligible ordinary fluid lacks source watched factors: " + block);
        }
        CompoundTag profile = new InstabilityProfile().save();
        check(profile.getCompound("Blocks").isEmpty(), "Empty profiler must not eagerly allocate a65536-cell array per mod fluid");
        check(profile.getCompound("Watchers").getAllKeys().equals(InstabilityProfile.FACTORS.keySet()), "Sparse profiler must still persist the complete registered watcher identity");
        long[] chunks = new long[401]; for (int i=0;i<chunks.length;i++) chunks[i]=i;
        profile.putLongArray("Chunks", chunks); profile.putIntArray("Solid", new int[65536]);
        var fluid = Registry.FLUID.get(new net.minecraft.resources.ResourceLocation(choices.get(0).payload().getString("Fluid")));
        String block = Registry.BLOCK.getKey(fluid.defaultFluidState().createLegacyBlock().getBlock()).toString();
        CompoundTag sampled = new CompoundTag(); int[] cells = new int[65536]; cells[64 << 8] = 401; sampled.putIntArray(block,cells); profile.put("Blocks",sampled);
        var restored = InstabilityProfile.load(profile);
        var factors = InstabilityProfile.FACTORS.get(block);
        check(restored.count()==401 && restored.score(java.util.Map.of())==Math.round(factors.accessible()+factors.total()), "Real fluid factors must participate in restored source frequency/accessibility scoring");

        CompoundTag saved = new CompoundTag(); saved.put("Profile",profile); saved.putBoolean("Enabled",true); saved.putInt("Difficulty",2);
        CompoundTag baseline = new CompoundTag(); baseline.putInt(block,7); saved.put("Baseline",baseline);
        var compatible = InstabilityWorldData.load(saved);
        check(compatible.profile().count()==401 && compatible.baseline()!=null, "Compatible watcher identity must retain sampled profile and automatic baseline");
        CompoundTag changed = saved.copy(); changed.getCompound("Profile").getCompound("Watchers").remove(block);
        var migrated = InstabilityWorldData.load(changed);
        check(migrated.profile().count()==0 && migrated.baseline()==null, "Changed watcher set must discard incompatible samples and automatic baseline without inventing values");
        changed.putBoolean("ExplicitBaseline",true);
        var explicit = InstabilityWorldData.load(changed);
        check(explicit.profile().count()==0 && explicit.baseline()!=null && explicit.baseline().get(block)==7,
                "Explicit configured baseline must survive watcher migration while incompatible samples reset");
        check(InstabilityProfile.FACTORS.containsKey(block), "Migration test must not mutate registered factors");
        return List.of("fluid factors: all"+choices.size()+" eligible registered fluid pages have finite source watched factors; sparse profiler avoids unused per-fluid arrays and restored fluid samples score",
                "fluid factors: persisted watcher identity retains compatible profiles, resets incompatible profile/automatic baseline and preserves an explicit operator baseline without invented values");
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
