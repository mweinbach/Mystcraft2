/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Methods Forge adds to {@link Screen}, injected into it on Fabric. Forge's tooltip overloads take the stack and font
 * for its tooltip events; Fabric has no such events, so they draw with the screen's font like vanilla.
 */
public interface IForgeScreen {

    private Screen self() {
        return (Screen) this;
    }

    default Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }

    default void renderTooltip(PoseStack poseStack, List<Component> tooltips, Optional<TooltipComponent> visualTooltipComponent, int mouseX, int mouseY,
          ItemStack stack) {
        self().renderTooltip(poseStack, tooltips, visualTooltipComponent, mouseX, mouseY);
    }

    default void renderComponentTooltip(PoseStack poseStack, List<? extends FormattedText> tooltips, int mouseX, int mouseY, @Nullable Font font,
          ItemStack stack) {
        self().renderTooltip(poseStack, Language.getInstance().getVisualOrder(List.<FormattedText>copyOf(tooltips)), mouseX, mouseY);
    }
}
