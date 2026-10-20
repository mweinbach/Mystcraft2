/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package it.zerono.mods.extremereactors.loader.forge.gamecontent.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.zerono.mods.extremereactors.ExtremeReactors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.BiFunction;

/**
 * The biome modifier codecs used by the ore generation data in {@code data/bigreactors/forge/biome_modifier}.
 */
public final class ForgeBiomes {

    private static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ExtremeReactors.MOD_ID);

    public static final RegistryObject<Codec<OreBiomeModifier>> OREGEN_YELLORITE = register("oregen_yellorite", OreBiomeModifier::yellorite);

    public static final RegistryObject<Codec<OreBiomeModifier>> OREGEN_ANGLESITE = register("oregen_anglesite", OreBiomeModifier::anglesite);

    public static final RegistryObject<Codec<OreBiomeModifier>> OREGEN_BENITOITE = register("oregen_benitoite", OreBiomeModifier::benitoite);

    public static void initialize(final IEventBus modBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modBus);
    }

    private static RegistryObject<Codec<OreBiomeModifier>> register(final String name,
                                                                    final BiFunction<HolderSet<Biome>, Holder<PlacedFeature>, OreBiomeModifier> factory) {
        return BIOME_MODIFIER_SERIALIZERS.register(name, () ->
                RecordCodecBuilder.create(builder -> builder.group(
                        Biome.LIST_CODEC.fieldOf("biomes").forGetter(OreBiomeModifier::validBiomes),
                        PlacedFeature.CODEC.fieldOf("feature").forGetter(OreBiomeModifier::feature)
                ).apply(builder, factory)));
    }

    private ForgeBiomes() {
    }
}
