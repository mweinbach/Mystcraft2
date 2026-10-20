package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.BuilderBlockEntity;
import ports.buildcraft.builders.BuilderMenu;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code GuiBuilder}: {@code builder.png} with the right-hand part of {@code builder_blueprint.png}
 * beside it, holding the 24 slots that show what the blueprint still needs and the four tank gauges.
 */
public final class BuilderScreen extends BCScreen<BuilderMenu> {
    private static final ResourceLocation TEXTURE_BLUEPRINT = BuildCraft.id("textures/gui/builder_blueprint.png");
    private static final int SIZE_X = 176, SIZE_BLUEPRINT_X = 256, SIZE_Y = 222, BLUEPRINT_WIDTH = 87;
    private static final int TANK_X = 179, TANK_Y = 145, TANK_WIDTH = 16, TANK_HEIGHT = 47, TANK_OVERLAY_U = 0, TANK_OVERLAY_V = 54;
    private static final int DISPLAY_X = 179, DISPLAY_Y = 18, DISPLAY_COLUMNS = 4, DISPLAY_ROWS = 6;

    public BuilderScreen(BuilderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/builder.png"), SIZE_BLUEPRINT_X, SIZE_Y);
    }

    @Nullable
    private BuilderBlockEntity builder() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof BuilderBlockEntity builder ? builder : null;
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);
        blit(poseStack, leftPos, topPos, 0, 0, SIZE_X, SIZE_Y);
        RenderSystem.setShaderTexture(0, TEXTURE_BLUEPRINT);
        blit(poseStack, leftPos + SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH, topPos, SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH, 0, BLUEPRINT_WIDTH, SIZE_Y);
        for (int tank = 0; tank < BuilderBlockEntity.TANKS; tank++) drawTank(poseStack, tank);
        List<ItemStack> display = display();
        for (int i = 0; i < Math.min(display.size(), DISPLAY_COLUMNS * DISPLAY_ROWS); i++) {
            int x = leftPos + DISPLAY_X + (i % DISPLAY_COLUMNS) * 18, y = topPos + DISPLAY_Y + (i / DISPLAY_COLUMNS) * 18;
            ItemStack stack = display.get(i);
            itemRenderer.renderAndDecorateItem(stack, x, y);
            itemRenderer.renderGuiItemDecorations(font, stack, x, y, String.valueOf(stack.getCount()));
        }
    }

    private List<ItemStack> display() {
        BuilderBlockEntity builder = builder();
        return builder == null ? List.of() : builder.getDisplayRequired();
    }

    private Fluid contents(int tank) {
        return menu.tankAmount(tank) > 0 ? menu.tankFluid(tank) : Fluids.EMPTY;
    }

    /** Upstream {@code WidgetFluidTank}: the fluid from the bottom up, with the gauge's scale drawn over it. */
    private void drawTank(PoseStack poseStack, int tank) {
        int x = leftPos + TANK_X + tank * 18, y = topPos + TANK_Y;
        Fluid fluid = contents(tank);
        if (fluid != Fluids.EMPTY) {
            int height = Math.max(1, Math.min(TANK_HEIGHT, menu.tankAmount(tank) * TANK_HEIGHT / BuilderBlockEntity.TANK_CAPACITY));
            TextureAtlasSprite sprite = ClientPlatform.get().fluidSprite(fluid);
            int colour = ClientPlatform.get().fluidColour(fluid);
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F, 1);
            for (int drawnY = 0; drawnY < height; drawnY += 16) {
                int partHeight = Math.min(16, height - drawnY);
                blit(poseStack, x, y + TANK_HEIGHT - drawnY - partHeight, getBlitOffset(), TANK_WIDTH, partHeight, sprite);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        RenderSystem.setShaderTexture(0, TEXTURE_BLUEPRINT);
        blit(poseStack, x, y, TANK_OVERLAY_U, TANK_OVERLAY_V, TANK_WIDTH, TANK_HEIGHT);
    }

    @Override
    protected int tankAt(double mouseX, double mouseY) {
        for (int tank = 0; tank < BuilderBlockEntity.TANKS; tank++) {
            int x = TANK_X + tank * 18;
            if (mouseX >= x && mouseX < x + TANK_WIDTH && mouseY >= TANK_Y && mouseY < TANK_Y + TANK_HEIGHT) return tank;
        }
        return -1;
    }

    /** Upstream's GUI drew no title or inventory label; the texture carries none either. */
    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {}

    @Override
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
        int tank = tankAt(mouseX - leftPos, mouseY - topPos);
        if (tank >= 0) {
            Fluid fluid = contents(tank);
            Component amount = Component.translatable("gui.buildcraft.fluid_amount", menu.tankAmount(tank), BuilderBlockEntity.TANK_CAPACITY);
            renderComponentTooltip(poseStack, fluid == Fluids.EMPTY ? List.of(Component.translatable("gui.buildcraft.empty"), amount)
                    : List.of(Platform.get().fluidName(fluid), amount), mouseX, mouseY);
            return;
        }
        List<ItemStack> display = display();
        int column = Math.floorDiv(mouseX - leftPos - DISPLAY_X, 18), row = Math.floorDiv(mouseY - topPos - DISPLAY_Y, 18);
        if (column < 0 || column >= DISPLAY_COLUMNS || row < 0 || row >= DISPLAY_ROWS) return;
        int index = column + row * DISPLAY_COLUMNS;
        if (index < display.size()) renderTooltip(poseStack, display.get(index), mouseX, mouseY);
    }
}
