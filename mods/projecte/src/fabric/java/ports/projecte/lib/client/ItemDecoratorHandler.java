/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The item decorators registered per item, drawn by a mixin after vanilla's slot decorations.
 */
public final class ItemDecoratorHandler {

    private static final Map<Item, List<IItemDecorator>> DECORATORS = new HashMap<>();

    private ItemDecoratorHandler() {
    }

    public static synchronized void register(Item item, IItemDecorator decorator) {
        DECORATORS.computeIfAbsent(item, i -> new ArrayList<>()).add(decorator);
    }

    public static void render(Font font, ItemStack stack, int xOffset, int yOffset, float blitOffset) {
        if (stack.isEmpty()) {
            return;
        }
        List<IItemDecorator> decorators = DECORATORS.get(stack.getItem());
        if (decorators == null) {
            return;
        }
        for (IItemDecorator decorator : decorators) {
            if (decorator.render(font, stack, xOffset, yOffset, blitOffset)) {
                RenderSystem.enableTexture();
                RenderSystem.enableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
        }
    }
}
