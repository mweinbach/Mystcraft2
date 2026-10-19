package net.mystcraft.minecraft.age.generation;

import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.age.AgeSymbolCompiler;

/** Stores resolved controls once; future compiler changes cannot silently alter saved-world generation. */
public final class FrozenAgePlan {
    public static final String KEY = "CompiledPlan";
    private FrozenAgePlan() {}
    public static CompoundTag write(AgeSymbolCompiler.Plan plan) {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 1);
        tag.putString("Terrain", plan.terrain()); tag.putString("Weather", plan.weather());
        if (plan.biome() != null) tag.putString("Biome", plan.biome().toString());
        tag.putBoolean("StarFissure", plan.starFissure());
        ListTag errors = new ListTag(); plan.unsupported().forEach(error -> errors.add(StringTag.valueOf(error)));
        tag.put("Unsupported", errors); return tag;
    }
    public static AgeSymbolCompiler.Plan read(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.contains("Terrain", Tag.TAG_STRING) || !tag.contains("Weather", Tag.TAG_STRING))
            throw new IllegalStateException("Unknown saved compiled Age plan");
        var errors = new ArrayList<String>(); var list = tag.getList("Unsupported", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) errors.add(list.getString(i));
        return new AgeSymbolCompiler.Plan(tag.getString("Terrain"),
                tag.contains("Biome", Tag.TAG_STRING) ? new ResourceLocation(tag.getString("Biome")) : null,
                tag.getString("Weather"), errors, tag.getBoolean("StarFissure"));
    }
}
