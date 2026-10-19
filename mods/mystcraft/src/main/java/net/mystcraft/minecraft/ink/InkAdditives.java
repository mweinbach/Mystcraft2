package net.mystcraft.minecraft.ink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.mystcraft.minecraft.writing.PageData;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla entries and ore-tag counterparts from pinned 1.12 InkEffects.init. */
public final class InkAdditives {
    public static final List<String> PROPERTIES = List.of(LinkModifiers.INTRA_LINKING, LinkModifiers.INTRA_LINKING_ONLY,
            LinkModifiers.GENERATE_PLATFORM, LinkModifiers.MAINTAIN_MOMENTUM, LinkModifiers.DISARM, LinkModifiers.RELATIVE);
    private InkAdditives() {}
    public static Map<String, Float> effects(ItemStack stack) {
        if (stack.is(Items.GUNPOWDER)) return Map.of(LinkModifiers.DISARM, .2F);
        if (stack.is(Items.MUSHROOM_STEW)) return Map.of(LinkModifiers.DISARM, .05F);
        if (stack.is(Items.CLAY_BALL)) return Map.of(LinkModifiers.GENERATE_PLATFORM, .25F);
        if (stack.is(Items.EXPERIENCE_BOTTLE)) return Map.of(LinkModifiers.INTRA_LINKING, .15F);
        if (stack.is(Items.ENDER_PEARL)) return Map.of(LinkModifiers.INTRA_LINKING, .15F, LinkModifiers.DISARM, .15F);
        if (stack.is(Items.FEATHER)) return Map.of(LinkModifiers.MAINTAIN_MOMENTUM, .15F);
        if (stack.is(Items.FIRE_CHARGE)) return Map.of(LinkModifiers.DISARM, .25F);
        if (stack.is(Items.BLACK_DYE) || stack.is(Items.INK_SAC) || tagged(stack, "forge:dyes/black") || tagged(stack, "c:black_dyes")) return Map.of("", .5F);
        if (dust(stack, "brass") || dust(stack, "bronze")) return Map.of(LinkModifiers.DISARM, .15F);
        if (dust(stack, "tin")) return Map.of(LinkModifiers.GENERATE_PLATFORM, .1F, LinkModifiers.INTRA_LINKING, .1F);
        if (dust(stack, "iron")) return Map.of(LinkModifiers.GENERATE_PLATFORM, .15F, LinkModifiers.INTRA_LINKING, .15F);
        if (dust(stack, "lead")) return Map.of(LinkModifiers.DISARM, .2F, LinkModifiers.INTRA_LINKING, .2F);
        if (dust(stack, "silver")) return Map.of(LinkModifiers.GENERATE_PLATFORM, .2F, LinkModifiers.INTRA_LINKING, .2F);
        if (dust(stack, "diamond")) return Map.of(LinkModifiers.INTRA_LINKING, .25F, LinkModifiers.MAINTAIN_MOMENTUM, .1F, LinkModifiers.GENERATE_PLATFORM, .1F);
        if (dust(stack, "gold")) return Map.of(LinkModifiers.INTRA_LINKING, .25F, LinkModifiers.GENERATE_PLATFORM, .1F, LinkModifiers.DISARM, .1F);
        return Map.of();
    }
    private static boolean dust(ItemStack stack, String metal) {
        return tagged(stack, "forge:dusts/" + metal) || tagged(stack, "c:" + metal + "_dusts") || tagged(stack, "c:" + metal + "_dust");
    }
    private static boolean tagged(ItemStack stack, String id) { return stack.is(TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(id))); }

    /** Matches the tile source: empty-name entries are skipped in BOTH sum and application. */
    public static void mix(Map<String, Float> ink, Map<String, Float> additive) {
        float total = 0;
        for (var entry : additive.entrySet()) if (!entry.getKey().isEmpty()) total += entry.getValue();
        float inverse = 1 - total;
        ink.replaceAll((key, value) -> value * inverse);
        for (var entry : additive.entrySet()) if (!entry.getKey().isEmpty()) ink.merge(entry.getKey(), entry.getValue(), Float::sum);
    }
    public static long apply(ItemStack panel, Map<String, Float> probabilities, long seed) {
        Random random = new Random(seed);
        for (var entry : probabilities.entrySet()) if (random.nextInt(100) < entry.getValue() * 100) PageData.addLinkProperty(panel, entry.getKey());
        return random.nextLong();
    }
    public static CompoundTag save(Map<String, Float> values) {
        CompoundTag data = new CompoundTag();
        values.forEach(data::putFloat);
        return data;
    }
    public static Map<String, Float> load(CompoundTag tag) {
        Map<String, Float> values = new HashMap<>();
        for (String key : PROPERTIES) {
            float value = tag.getFloat(key);
            if (Float.isFinite(value) && value > 0 && value <= 1) values.put(key, value);
        }
        return values;
    }
}
