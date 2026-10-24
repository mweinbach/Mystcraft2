/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client;

import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;

/**
 * The block render type names a model's {@code render_type} may use, with the item render type that goes with each.
 */
public final class NamedRenderTypeManager {

    private static Map<ResourceLocation, RenderTypeGroup> renderTypes;

    private NamedRenderTypeManager() {
    }

    public static RenderTypeGroup get(ResourceLocation name) {
        if (renderTypes == null) {
            renderTypes = Map.of(
                  new ResourceLocation("solid"), new RenderTypeGroup(RenderType.solid(), Sheets.solidBlockSheet()),
                  new ResourceLocation("cutout"), new RenderTypeGroup(RenderType.cutout(), Sheets.cutoutBlockSheet()),
                  new ResourceLocation("cutout_mipped"), new RenderTypeGroup(RenderType.cutoutMipped(), Sheets.cutoutBlockSheet()),
                  new ResourceLocation("cutout_mipped_all"), new RenderTypeGroup(RenderType.cutoutMipped(), Sheets.cutoutBlockSheet()),
                  new ResourceLocation("translucent"), new RenderTypeGroup(RenderType.translucent(), Sheets.translucentCullBlockSheet(), Sheets.translucentItemSheet()),
                  new ResourceLocation("tripwire"), new RenderTypeGroup(RenderType.tripwire(), Sheets.translucentCullBlockSheet(), Sheets.translucentItemSheet())
            );
        }
        return renderTypes.getOrDefault(name, RenderTypeGroup.EMPTY);
    }
}
