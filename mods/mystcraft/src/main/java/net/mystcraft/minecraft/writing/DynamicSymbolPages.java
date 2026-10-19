package net.mystcraft.minecraft.writing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Registry-scoped page identities. Never caches a server's dynamic registry or a previous connection's biomes. */
public final class DynamicSymbolPages {
    public record Choice(CompoundTag payload, Integer rank, Component name, List<String> poem) {
        public Choice { payload = payload.copy(); name = name.copy(); poem = List.copyOf(poem); }
        @Override public CompoundTag payload() { return payload.copy(); }
        @Override public Component name() { return name.copy(); }
    }
    private static Supplier<RegistryAccess> clientRegistries = () -> null;
    private static java.util.function.Predicate<Fluid> fluidEligibility = fluid -> true;
    private static java.util.function.Function<Fluid, Component> fluidName = fluid -> fluid.defaultFluidState().createLegacyBlock().getBlock().getName();
    private DynamicSymbolPages() {}
    /** Client-only adapter supplies the current level dynamically; a disconnected menu returns null. */
    public static void setClientRegistries(Supplier<RegistryAccess> registries) { clientRegistries = registries; }
    public static RegistryAccess clientRegistries() { return clientRegistries.get(); }
    /** Loader adapter may exclude gaseous/negative-density fluids using its native attributes API. */
    public static void setFluidEligibility(java.util.function.Predicate<Fluid> eligible) { fluidEligibility = java.util.Objects.requireNonNull(eligible); }
    public static void setFluidName(java.util.function.Function<Fluid, Component> names) { fluidName = java.util.Objects.requireNonNull(names); }
    public static Component fluidDisplayName(Fluid fluid) { return fluidName.apply(fluid).copy(); }
    public static List<Choice> choices(RegistryAccess registries) {
        List<Choice> result = new ArrayList<>();
        if (registries != null) {
            var biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
            for (var biome : biomes) {
                ResourceLocation id = biomes.getKey(biome); if (id == null) continue;
                var rule = SymbolTuning.biome(id); if (rule.blacklisted()) continue;
                CompoundTag page = new CompoundTag(); page.putString("symbol", "mystcraft:biome/" + id.getNamespace() + "/" + id.getPath());
                result.add(new Choice(page, SymbolTuning.cardRank(page.getString("symbol"), rule.cardRank()),
                        displayName(page), poem(page)));
            }
            result.addAll(fluidChoices());
        }
        return List.copyOf(result);
    }
    public static List<Choice> fluidChoices() {
        List<Choice> result = new ArrayList<>();
        for (Fluid fluid : Registry.FLUID) {
            if (fluid == Fluids.EMPTY || fluid == Fluids.WATER || fluid == Fluids.LAVA || !fluid.defaultFluidState().isSource()) continue;
            var state = fluid.defaultFluidState().createLegacyBlock();
            if (state.isAir() || !state.getMaterial().isLiquid() || !fluidEligibility.test(fluid)) continue;
            ResourceLocation id = Registry.FLUID.getKey(fluid); if (id == null) continue;
            var rule = SymbolTuning.fluid(fluid, fluidDisplayName(fluid)); if (rule.blacklisted()) continue;
            CompoundTag page = new CompoundTag(); page.putString("symbol", "mystcraft:fluid/" + id.getNamespace() + "/" + id.getPath());
            page.putString("Fluid", id.toString()); page.put("BlockState", NbtUtils.writeBlockState(state));
            page.putBoolean("Sea", !rule.seaBanned()); page.putBoolean("FluidMaterial", true);
            result.add(new Choice(page, SymbolTuning.cardRank(page.getString("symbol"), rule.cardRank()), displayName(page), poem(page)));
        }
        return List.copyOf(result);
    }
    public static boolean isDynamic(ItemStack page) { return isDynamic(PageData.payload(page)); }
    public static boolean isDynamic(CompoundTag page) {
        return biome(page) != null || PageData.symbol(page).map(id -> id.getNamespace().equals("mystcraft")
                && (id.getPath().startsWith("fluid/") || id.getPath().equals("material")) && page.contains("BlockState", Tag.TAG_COMPOUND)).orElse(false);
    }
    public static Component displayName(ItemStack page) { return displayName(PageData.payload(page)); }
    public static Component displayName(CompoundTag page) {
        ResourceLocation biome = biome(page);
        if (biome != null) return Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath().replace('/', '.'));
        var nativeFluid = ResourceLocation.tryParse(page.getString("Fluid"));
        if (nativeFluid != null && Registry.FLUID.containsKey(nativeFluid)) return fluidName.apply(Registry.FLUID.get(nativeFluid)).copy();
        var state = page.getCompound("BlockState"); var block = ResourceLocation.tryParse(state.getString("Name"));
        if (block != null && Registry.BLOCK.containsKey(block)) return Registry.BLOCK.get(block).getName();
        return Component.literal(page.getString("symbol"));
    }
    public static List<String> poem(ItemStack page) { return poem(PageData.payload(page)); }
    public static List<String> poem(CompoundTag page) {
        ResourceLocation biome = biome(page);
        if (biome != null) return List.of("Nature", "Nurture", "Encourage", biome.toString());
        return List.of("Transform", "Constraint", "Flow", page.getCompound("BlockState").getString("Name"));
    }
    private static ResourceLocation biome(CompoundTag page) {
        var symbol = PageData.symbol(page); if (symbol.isEmpty() || !symbol.get().getNamespace().equals("mystcraft")) return null;
        String path = symbol.get().getPath();
        if (path.equals("biome")) return ResourceLocation.tryParse(page.getString("Biome"));
        if (!path.startsWith("biome/")) return null;
        String[] split = path.substring(6).split("/", 2);
        return split.length == 2 ? ResourceLocation.tryParse(split[0] + ":" + split[1]) : null;
    }
}
