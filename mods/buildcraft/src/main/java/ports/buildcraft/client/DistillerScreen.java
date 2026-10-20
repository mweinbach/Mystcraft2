package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.RefineryRecipes;
import ports.buildcraft.factory.DistillerBlockEntity;
import ports.buildcraft.factory.DistillerMenu;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code GuiDistiller}: the input, gas and liquid gauges. While distilling, streaks of each
 * fluid's colour run from the input through the column to the outputs; while idle, the column marks a
 * distillable input and the outputs that are full or hold the wrong fluid.
 */
public final class DistillerScreen extends BCScreen<DistillerMenu> {
    private record Area(int x, int y, int width, int height) {
        boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private static final int TEXTURE_SIZE = 256;
    private static final Area[] TANKS = {new Area(44, 23, 16, 38), new Area(98, 10, 34, 17), new Area(98, 54, 34, 17)};
    private static final int VERTICAL_OVERLAY_U = 0, HORIZONTAL_OVERLAY_U = 17, OVERLAY_V = 161;
    private static final Area OFF_VALID_INPUT = new Area(61, 26, 17, 29);
    private static final Area OFF_GAS = new Area(77, 12, 20, 13);
    private static final Area OFF_LIQUID = new Area(77, 56, 20, 13);
    private static final Area ACTIVE_BACKGROUND = new Area(61, 12, 36, 57);
    private static final Area ANIM_1 = new Area(61, 38, 26, 5);
    private static final Area ANIM_2A = new Area(74, 25, 13, 16), ANIM_2B = new Area(74, 40, 13, 16);
    private static final Area ANIM_3A = new Area(79, 17, 3, 8), ANIM_3B = new Area(79, 56, 3, 8);
    private static final Area ANIM_4A = new Area(79, 17, 18, 3), ANIM_4B = new Area(79, 61, 18, 3);
    /** Texture positions of the white masks for each animation area, relative to the area's position. */
    private static final int ANIM_U = 151, ANIM_V = 102;
    /** Pixels per second, where the streak's length and position are measured in mask area. */
    private static final double ACTIVE_SPEED = 300;
    private static final double ACTIVE_PIXEL_WIDTH = 150;

    private long lastActiveTime = -1;
    private double activeStart = -ACTIVE_PIXEL_WIDTH;
    private double activeEnd;

    public DistillerScreen(DistillerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/distiller.png"), 176, 161);
        inventoryLabelY = 161 - 96;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 6;
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        Fluid input = menu.tankFluid(DistillerMenu.TANK_IN);
        Fluid gas = contents(DistillerMenu.TANK_GAS);
        Fluid liquid = contents(DistillerMenu.TANK_LIQUID);
        RenderSystem.setShaderTexture(0, texture);
        if (menu.isActive()) {
            blit(poseStack, leftPos + ACTIVE_BACKGROUND.x, topPos + ACTIVE_BACKGROUND.y, 176, 57, ACTIVE_BACKGROUND.width, ACTIVE_BACKGROUND.height);
            animate(poseStack, FluidColours.average(contents(DistillerMenu.TANK_IN)), FluidColours.average(gas), FluidColours.average(liquid));
        } else {
            activeStart = -ACTIVE_PIXEL_WIDTH;
            activeEnd = 0;
            lastActiveTime = -1;
            RefineryRecipes.Distillation recipe = RefineryRecipes.distillation(input);
            if (recipe != null) blit(poseStack, leftPos + OFF_VALID_INPUT.x, topPos + OFF_VALID_INPUT.y, 176, 14, OFF_VALID_INPUT.width, OFF_VALID_INPUT.height);
            if (blocked(DistillerMenu.TANK_GAS, recipe == null ? null : recipe.outGas().fluid())) {
                blit(poseStack, leftPos + OFF_GAS.x, topPos + OFF_GAS.y, 192, 0, OFF_GAS.width, OFF_GAS.height);
            }
            if (blocked(DistillerMenu.TANK_LIQUID, recipe == null ? null : recipe.outLiquid().fluid())) {
                blit(poseStack, leftPos + OFF_LIQUID.x, topPos + OFF_LIQUID.y, 192, 44, OFF_LIQUID.width, OFF_LIQUID.height);
            }
        }
        for (int tank = 0; tank < TANKS.length; tank++) drawTank(poseStack, tank);
    }

    private Fluid contents(int tank) {
        return menu.tankAmount(tank) > 0 ? menu.tankFluid(tank) : Fluids.EMPTY;
    }

    /** An output is blocked when it is full or holds a fluid other than the recipe's. */
    private boolean blocked(int tank, Fluid expected) {
        int amount = menu.tankAmount(tank);
        if (amount <= 0) return false;
        return amount >= DistillerBlockEntity.CAPACITY || expected != null && menu.tankFluid(tank) != expected;
    }

    private void drawTank(PoseStack poseStack, int tank) {
        Area area = TANKS[tank];
        Fluid fluid = contents(tank);
        int x = leftPos + area.x, y = topPos + area.y;
        if (fluid != Fluids.EMPTY) {
            int height = Math.max(1, Math.min(area.height, menu.tankAmount(tank) * area.height / DistillerBlockEntity.CAPACITY));
            boolean gaseous = Platform.get().isGaseous(fluid);
            TextureAtlasSprite sprite = ClientPlatform.get().fluidSprite(fluid);
            int colour = ClientPlatform.get().fluidColour(fluid);
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F, 1);
            for (int drawnY = 0; drawnY < height; drawnY += 16) {
                int partHeight = Math.min(16, height - drawnY);
                int partY = gaseous ? y + drawnY : y + area.height - drawnY - partHeight;
                for (int drawnX = 0; drawnX < area.width; drawnX += 16) {
                    blit(poseStack, x + drawnX, partY, getBlitOffset(), Math.min(16, area.width - drawnX), partHeight, sprite);
                }
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        RenderSystem.setShaderTexture(0, texture);
        blit(poseStack, x, y, tank == DistillerMenu.TANK_IN ? VERTICAL_OVERLAY_U : HORIZONTAL_OVERLAY_U, OVERLAY_V, area.width, area.height);
    }

    /** Upstream {@code drawBackgroundLayer} while active: ten streaks, a quarter of their spacing apart. */
    private void animate(PoseStack poseStack, int input, int gas, int liquid) {
        long now = Util.getMillis();
        if (lastActiveTime != -1) {
            double change = ACTIVE_SPEED * (now - lastActiveTime) / 1000.0;
            activeStart += change;
            activeEnd += change;
        }
        lastActiveTime = now;
        double startStore = activeStart, endStore = activeEnd, distance = 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < 10; i++) {
            distance = drawStreak(poseStack, input, gas, liquid);
            activeStart -= distance * 3 / 4;
            activeEnd -= distance * 3 / 4;
        }
        activeStart = startStore;
        activeEnd = endStore;
        if (activeStart >= distance * 3) {
            activeStart -= distance * 3 / 4;
            activeEnd -= distance * 3 / 4;
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private double drawStreak(PoseStack poseStack, int input, int gas, int liquid) {
        double distance = area(ANIM_1);
        drawPart(poseStack, gas, ANIM_2A, 0, distance, Flow.UP);
        drawPart(poseStack, liquid, ANIM_2B, 0, distance, Flow.DOWN);
        distance += area(ANIM_2A);
        drawPart(poseStack, gas, ANIM_3A, 0, distance, Flow.UP);
        drawPart(poseStack, liquid, ANIM_3B, 0, distance, Flow.DOWN);
        distance += area(ANIM_3A);
        drawPart(poseStack, gas, ANIM_4A, 0, distance, Flow.RIGHT);
        drawPart(poseStack, liquid, ANIM_4B, 0, distance, Flow.RIGHT);
        distance += area(ANIM_4A);

        drawPart(poseStack, input, ANIM_1, -36, 0, Flow.RIGHT);
        drawPart(poseStack, input, ANIM_2A, -36, area(ANIM_1), Flow.UP);
        drawPart(poseStack, input, ANIM_2B, -36, area(ANIM_1), Flow.DOWN);
        return distance;
    }

    private enum Flow { RIGHT, UP, DOWN }

    private static double area(Area area) {
        return area.width * area.height;
    }

    /**
     * Draws the part of the streak inside {@code area}, starting {@code startPoint} along it, with the
     * white mask tinted {@code colour}. The input's masks lie 36 pixels left of the outputs'.
     */
    private void drawPart(PoseStack poseStack, int colour, Area area, int uOffset, double startPoint, Flow flow) {
        double size = area(area);
        double start = (activeStart - startPoint) / size, end = (activeEnd - startPoint) / size;
        if (end < 0 || start >= 1) return;
        double from = Math.max(0, start), to = Math.min(1, end);
        float u0, v0, u1, v1;
        switch (flow) {
            case RIGHT -> { u0 = (float) from; v0 = 0; u1 = (float) to; v1 = 1; }
            case UP -> { u0 = 0; v0 = (float) (1 - to); u1 = 1; v1 = (float) (1 - from); }
            case DOWN -> { u0 = 0; v0 = (float) from; u1 = 1; v1 = (float) to; }
            default -> throw new IllegalStateException(flow.name());
        }
        float x = leftPos + area.x, y = topPos + area.y, maskU = area.x + ANIM_U + uOffset, maskV = area.y + ANIM_V;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F, 1);
        blitScaled(poseStack, x + u0 * area.width, y + v0 * area.height, x + u1 * area.width, y + v1 * area.height,
                maskU + u0 * area.width, maskV + v0 * area.height, maskU + u1 * area.width, maskV + v1 * area.height);
    }

    private void blitScaled(PoseStack poseStack, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        Matrix4f pose = poseStack.last().pose();
        float z = getBlitOffset(), scale = 1F / TEXTURE_SIZE;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(pose, x0, y1, z).uv(u0 * scale, v1 * scale).endVertex();
        builder.vertex(pose, x1, y1, z).uv(u1 * scale, v1 * scale).endVertex();
        builder.vertex(pose, x1, y0, z).uv(u1 * scale, v0 * scale).endVertex();
        builder.vertex(pose, x0, y0, z).uv(u0 * scale, v0 * scale).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    @Override
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
        for (int tank = 0; tank < TANKS.length; tank++) {
            if (!TANKS[tank].contains(mouseX - leftPos, mouseY - topPos)) continue;
            Fluid fluid = contents(tank);
            Component amount = Component.translatable("gui.buildcraft.fluid_amount", menu.tankAmount(tank), DistillerBlockEntity.CAPACITY);
            List<Component> lines = fluid == Fluids.EMPTY
                    ? List.of(Component.translatable("gui.buildcraft.empty"), amount)
                    : List.of(Platform.get().fluidName(fluid), amount);
            renderComponentTooltip(poseStack, lines, mouseX, mouseY);
        }
    }
}
