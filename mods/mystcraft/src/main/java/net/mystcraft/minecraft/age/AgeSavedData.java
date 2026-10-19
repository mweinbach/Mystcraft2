package net.mystcraft.minecraft.age;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.mystcraft.minecraft.writing.PageData;

/** Canonical Age identity/seed/pages/state, stored with the server's overworld data. */
public final class AgeSavedData extends SavedData {
    public static final String FILE = "mystcraft_ages";
    private long nextNumber = 1;
    private final Map<UUID, Entry> ages = new LinkedHashMap<>();

    public record Entry(UUID id, long number, long seed, String title, List<CompoundTag> pages, CompoundTag state) {
        public Entry { pages = pages.stream().map(CompoundTag::copy).toList(); }
        public ResourceLocation dimensionId() { return new ResourceLocation("mystcraft", "age_" + number); }
    }
    public Collection<Entry> entries() { return List.copyOf(ages.values()); }
    public Entry get(UUID id) { return ages.get(id); }
    public Entry allocate(long seed, String title, List<CompoundTag> pages) {
        if (!AgeBookData.validPages(pages)) throw new IllegalArgumentException("Invalid descriptive pages");
        if (nextNumber == Long.MAX_VALUE) throw new IllegalStateException("Age number space exhausted");
        CompoundTag state = new CompoundTag();
        AgeSymbolCompiler.Plan plan = AgeSymbolCompiler.parse(pages);
        state.put(net.mystcraft.minecraft.age.generation.FrozenAgePlan.KEY, net.mystcraft.minecraft.age.generation.FrozenAgePlan.write(plan));
        boolean classic = net.mystcraft.minecraft.age.generation.ClassicTerrainSpec.supports(plan.terrain());
        int generatorVersion = classic ? net.mystcraft.minecraft.age.generation.ClassicTerrainSpec.fromPlan(plan).version() : 0;
        state.putInt("GeneratorVersion", generatorVersion);
        state.putString("GeneratorBackend", classic ? "classic_v" + generatorVersion : "vanilla_v0");
        if (classic) state.put("GeneratorSpec", net.mystcraft.minecraft.age.generation.ClassicTerrainSpec.fromPlan(plan).toTag());
        state.put(net.mystcraft.minecraft.age.visual.AgeVisualSettings.STATE_KEY,
                net.mystcraft.minecraft.age.visual.AgeVisualCompiler.parse(pages, seed).settings().toTag());
        Entry entry = new Entry(UUID.randomUUID(), nextNumber++, seed, title, pages, state);
        ages.put(entry.id(), entry); setDirty(); return entry;
    }
    /** Production allocation uses a validated frozen compilation; old fixture allocation stays explicit above. */
    public Entry allocatePrepared(long seed, String title, List<CompoundTag> originalPages, AgeAllocation.Prepared prepared) {
        if (!AgeBookData.validPages(originalPages) || !prepared.compilation().successful())
            throw new IllegalArgumentException("Invalid descriptive compilation");
        CompoundTag state = prepared.state();
        if (!state.contains(AgeAllocation.EXECUTION, Tag.TAG_COMPOUND) || state.getInt("ClassicRuntimeVersion") != 1)
            throw new IllegalArgumentException("Incomplete frozen Age compilation");
        if (nextNumber == Long.MAX_VALUE) throw new IllegalStateException("Age number space exhausted");
        Entry entry = new Entry(UUID.randomUUID(), nextNumber++, seed, title, originalPages, state);
        ages.put(entry.id(), entry); setDirty(); return entry;
    }

    public static AgeSavedData load(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalStateException("Unsupported Mystcraft Age save version");
        AgeSavedData result = new AgeSavedData();
        result.nextNumber = Math.max(1, tag.getLong("NextNumber"));
        ListTag list = tag.getList("Ages", Tag.TAG_COMPOUND);
        java.util.Set<Long> numbers = new java.util.HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            if (!saved.hasUUID("Id") || saved.getLong("Number") < 1
                    || saved.getLong("Number") == Long.MAX_VALUE || !saved.contains("Seed", Tag.TAG_LONG))
                throw new IllegalStateException("Invalid saved Mystcraft Age identity");
            Entry entry = new Entry(saved.getUUID("Id"), saved.getLong("Number"), saved.getLong("Seed"),
                    saved.getString("Title"), PageData.readPages(saved), saved.getCompound("State").copy());
            if (!AgeBookData.validPages(entry.pages()) || !numbers.add(entry.number())
                    || result.ages.putIfAbsent(entry.id(), entry) != null)
                throw new IllegalStateException("Duplicate or invalid saved Mystcraft Age");
            result.nextNumber = Math.max(result.nextNumber, entry.number() + 1);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 1); tag.putLong("NextNumber", nextNumber);
        ListTag list = new ListTag();
        for (Entry entry : ages.values()) {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("Id", entry.id()); saved.putLong("Number", entry.number());
            saved.putLong("Seed", entry.seed()); saved.putString("Title", entry.title());
            PageData.writePages(saved, entry.pages()); saved.put("State", entry.state().copy()); list.add(saved);
        }
        tag.put("Ages", list); return tag;
    }
}
