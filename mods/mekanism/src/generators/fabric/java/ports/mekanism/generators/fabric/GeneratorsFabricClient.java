/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.generators.fabric;

import mekanism.generators.client.render.item.RenderWindGeneratorItem;
import mekanism.generators.common.registries.GeneratorsBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

/**
 * The wind generator item is a Forge built-in item renderer ({@code builtin/entity}). Fabric draws that model through
 * {@link BuiltinItemRendererRegistry}, using the same renderer Forge's BEWLR calls.
 */
public final class GeneratorsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BuiltinItemRendererRegistry.INSTANCE.register(GeneratorsBlocks.WIND_GENERATOR.asItem(),
              (stack, mode, matrices, consumers, light, overlay) ->
                    RenderWindGeneratorItem.RENDERER.renderByItem(stack, mode, matrices, consumers, light, overlay));
    }
}
