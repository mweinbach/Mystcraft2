/*
 * Mekanism 1.19.2 port - Fabric-only in-world tests.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import ports.mekanism.lib.client.model.ModelLoaderKey;

/**
 * The port reads the Forge {@code loader} key of every model JSON, from every mod. In Tekxit 4 it failed the models of
 * loaders it did not know (AE2, CTM, Twilight Forest, Porting Lib's {@code forge:item_layers} and others) with "Unknown
 * model loader"; those must be left to the mod that registers the loader.
 */
public class FabricModelJsonGameTests {

    @GameTest(template = TEMPLATE)
    public static void unknownModelLoadersAreLeftToOtherMods(GameTestHelper helper) {
        Map<ResourceLocation, String> loaders = Map.of(new ResourceLocation("forge", "obj"), "obj loader");
        List<ResourceLocation> reported = new ArrayList<>();

        check("obj loader".equals(ModelLoaderKey.resolve(json("{\"loader\": \"forge:obj\"}"), loaders::get, reported::add)),
              "A loader the port registers was not resolved");
        check(ModelLoaderKey.resolve(json("{\"parent\": \"item/generated\"}"), loaders::get, reported::add) == null,
              "A model without a loader key resolved a loader");
        // Throwing here fails the other mod's model; the reader must answer null instead.
        check(ModelLoaderKey.resolve(json("{\"loader\": \"mekanism_gametest:other_mods_loader\"}"), loaders::get, reported::add) == null,
              "A loader the port does not register resolved to something");
        ModelLoaderKey.resolve(json("{\"loader\": \"mekanism_gametest:other_mods_loader\"}"), loaders::get, reported::add);
        check(reported.equals(List.of(new ResourceLocation("mekanism_gametest", "other_mods_loader"))),
              "An unknown loader was not reported exactly once: " + reported);
        helper.succeed();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
