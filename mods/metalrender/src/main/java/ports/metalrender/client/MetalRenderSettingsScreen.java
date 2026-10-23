/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import ports.metalrender.config.MetalRenderConfig;

/** Settings screen reached from Video Settings. Changes apply when the screen closes. */
final class MetalRenderSettingsScreen extends Screen {

    private static final List<Float> SCALES = List.of(0.5f, 0.59f, 0.67f, 0.75f, 0.85f, 1.0f);

    private final Screen parent;
    private boolean enabled;
    private MetalRenderConfig.Tier tier;
    private MetalRenderConfig.Upscaler upscaler;
    private float scale;
    private boolean meshShaders;
    private boolean debugInfo;

    MetalRenderSettingsScreen(final Screen parent) {
        super(Component.translatable("metalrender.settings.title"));
        this.parent = parent;
        final MetalRenderConfig config = MetalRenderClient.config();
        enabled = config.enabled;
        tier = config.tier;
        upscaler = config.upscaler;
        scale = nearestScale(config.resolutionScale);
        meshShaders = config.meshShaders;
        debugInfo = config.debugInfo;
    }

    private static float nearestScale(final float value) {
        float best = SCALES.get(SCALES.size() - 1);
        for (final float candidate : SCALES) {
            if (Math.abs(candidate - value) < Math.abs(best - value)) {
                best = candidate;
            }
        }
        return best;
    }

    @Override
    protected void init() {
        final int left = width / 2 - 155;
        final int right = width / 2 + 5;
        int y = height / 6;
        addRenderableWidget(CycleButton.onOffBuilder(enabled)
                .create(left, y, 150, 20, Component.translatable("metalrender.settings.enabled"), (b, v) -> enabled = v));
        addRenderableWidget(CycleButton.<MetalRenderConfig.Tier>builder(
                        t -> Component.translatable("metalrender.tier." + t.id()))
                .withValues(MetalRenderConfig.Tier.values()).withInitialValue(tier)
                .create(right, y, 150, 20, Component.translatable("metalrender.settings.tier"), (b, v) -> tier = v));
        y += 24;
        addRenderableWidget(CycleButton.<MetalRenderConfig.Upscaler>builder(
                        u -> Component.translatable("metalrender.upscaler." + u.id()))
                .withValues(MetalRenderConfig.Upscaler.values()).withInitialValue(upscaler)
                .create(left, y, 150, 20, Component.translatable("metalrender.settings.upscaler"), (b, v) -> upscaler = v));
        addRenderableWidget(CycleButton.<Float>builder(s -> Component.literal(Math.round(s * 100.0f) + "%"))
                .withValues(SCALES).withInitialValue(scale)
                .create(right, y, 150, 20, Component.translatable("metalrender.settings.scale"), (b, v) -> scale = v));
        y += 24;
        addRenderableWidget(CycleButton.onOffBuilder(meshShaders)
                .create(left, y, 150, 20, Component.translatable("metalrender.settings.mesh_shaders"), (b, v) -> meshShaders = v));
        addRenderableWidget(CycleButton.onOffBuilder(debugInfo)
                .create(right, y, 150, 20, Component.translatable("metalrender.settings.debug_info"), (b, v) -> debugInfo = v));
        addRenderableWidget(new Button(width / 2 - 100, height - 27, 200, 20, CommonComponents.GUI_DONE, b -> onClose()));
    }

    @Override
    public void render(final PoseStack poseStack, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(poseStack);
        drawCenteredString(poseStack, font, title, width / 2, 15, 0xFFFFFF);
        drawCenteredString(poseStack, font, Component.literal(MetalRenderClient.status()), width / 2, 30, 0xA0A0A0);
        drawCenteredString(poseStack, font, Component.literal(MetalRenderClient.capabilities().summary()),
                width / 2, height / 6 + 3 * 24 + 8, 0x808080);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        final MetalRenderConfig config = MetalRenderClient.config();
        final boolean tierChanged = config.tier != tier;
        config.enabled = enabled;
        config.tier = tier;
        config.upscaler = upscaler;
        config.resolutionScale = scale;
        config.meshShaders = meshShaders;
        config.debugInfo = debugInfo;
        MetalRenderClient.applyConfig(tierChanged);
        minecraft.setScreen(parent);
    }
}
