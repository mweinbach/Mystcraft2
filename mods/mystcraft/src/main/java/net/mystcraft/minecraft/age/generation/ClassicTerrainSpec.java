package net.mystcraft.minecraft.age.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.age.AgeSymbolCompiler;

/** Immutable version-one terrain inputs. Unknown versions/modes fail instead of regenerating differently. */
public record ClassicTerrainSpec(int version, String terrain, ResourceLocation fill, ResourceLocation sea,
                                 int ground, int seaLevel, boolean bedrock) {
    private static final Set<String> MODES = Set.of("flat", "void", "nether", "end", "normal", "amplified");
    public static final Codec<ClassicTerrainSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(ClassicTerrainSpec::version),
            Codec.STRING.fieldOf("terrain").forGetter(ClassicTerrainSpec::terrain),
            ResourceLocation.CODEC.fieldOf("fill").forGetter(ClassicTerrainSpec::fill),
            ResourceLocation.CODEC.fieldOf("sea").forGetter(ClassicTerrainSpec::sea),
            Codec.INT.fieldOf("ground").forGetter(ClassicTerrainSpec::ground),
            Codec.INT.fieldOf("sea_level").forGetter(ClassicTerrainSpec::seaLevel),
            Codec.BOOL.fieldOf("bedrock").forGetter(ClassicTerrainSpec::bedrock)
    ).apply(instance, ClassicTerrainSpec::new));
    public ClassicTerrainSpec {
        if ((version != 1 && version != 2 && version != 3 && version != 4) || !MODES.contains(terrain)
                || (version == 1 && (terrain.equals("normal") || terrain.equals("amplified")))
                || (version == 2 && !(terrain.equals("normal") || terrain.equals("amplified"))) || ground < 1 || ground > 255 || seaLevel < 0 || seaLevel > 255)
            throw new IllegalArgumentException("Unsupported classic terrain specification");
    }
    public static boolean supports(String terrain) { return MODES.contains(terrain); }
    public static ClassicTerrainSpec fromPlan(AgeSymbolCompiler.Plan plan) {
        return new ClassicTerrainSpec(plan.terrain().equals("normal") || plan.terrain().equals("amplified") ? 2 : 1, plan.terrain(), new ResourceLocation("minecraft", "stone"),
                new ResourceLocation("minecraft", plan.terrain().equals("end") || plan.terrain().equals("void") ? "air" : "water"),
                64, plan.terrain().equals("nether") ? 32 : plan.terrain().equals("end") ? 49 : 63,
                !plan.terrain().equals("end") && !plan.terrain().equals("void"));
    }
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", version); tag.putString("Terrain", terrain);
        tag.putString("Fill", fill.toString()); tag.putString("Sea", sea.toString()); tag.putInt("Ground", ground);
        tag.putInt("SeaLevel", seaLevel); tag.putBoolean("Bedrock", bedrock); return tag;
    }
    public static ClassicTerrainSpec fromTag(CompoundTag tag) {
        return new ClassicTerrainSpec(tag.getInt("Version"), tag.getString("Terrain"),
                new ResourceLocation(tag.getString("Fill")), new ResourceLocation(tag.getString("Sea")),
                tag.getInt("Ground"), tag.getInt("SeaLevel"), tag.getBoolean("Bedrock"));
    }
}
