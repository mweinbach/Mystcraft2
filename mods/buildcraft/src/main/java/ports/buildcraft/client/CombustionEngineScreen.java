package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.CombustionEngineBlockEntity;
import ports.buildcraft.energy.CombustionEngineMenu;
import ports.buildcraft.platform.Platform;

/** Upstream {@code GuiEngineIron_BC8}: fuel, coolant and residue gauges with the glass overlay. */
public final class CombustionEngineScreen extends BCScreen<CombustionEngineMenu> {
    private static final int[] TANK_X = {26, 80, 134};
    private static final int TANK_Y = 18, TANK_WIDTH = 16, TANK_HEIGHT = 60;

    public CombustionEngineScreen(CombustionEngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/combustion_engine.png"), 176, 177);
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        for (int tank = 0; tank < 3; tank++) {
            Fluid fluid = menu.tankFluid(tank);
            int amount = menu.tankAmount(tank);
            int x = leftPos + TANK_X[tank], bottom = topPos + TANK_Y + TANK_HEIGHT;
            if (fluid != Fluids.EMPTY && amount > 0) {
                int height = Math.max(1, Math.min(TANK_HEIGHT, amount * TANK_HEIGHT / CombustionEngineBlockEntity.MAX_FLUID));
                TextureAtlasSprite sprite = ClientPlatform.get().fluidSprite(fluid);
                int colour = ClientPlatform.get().fluidColour(fluid);
                RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
                RenderSystem.setShaderColor((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F, 1);
                for (int drawn = 0; drawn < height; drawn += 16) {
                    int part = Math.min(16, height - drawn);
                    blit(poseStack, x, bottom - drawn - part, getBlitOffset(), TANK_WIDTH, part, sprite);
                }
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            RenderSystem.setShaderTexture(0, texture);
            blit(poseStack, x, topPos + TANK_Y, 176, 0, TANK_WIDTH, TANK_HEIGHT);
        }
    }

    @Override
    protected void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        super.renderTooltip(poseStack, mouseX, mouseY);
        for (int tank = 0; tank < 3; tank++) {
            int x = leftPos + TANK_X[tank], y = topPos + TANK_Y;
            if (mouseX < x || mouseX >= x + TANK_WIDTH || mouseY < y || mouseY >= y + TANK_HEIGHT) continue;
            Fluid fluid = menu.tankFluid(tank);
            Component amount = Component.translatable("gui.buildcraft.fluid_amount", menu.tankAmount(tank), CombustionEngineBlockEntity.MAX_FLUID);
            List<Component> lines = fluid == Fluids.EMPTY || menu.tankAmount(tank) <= 0
                    ? List.of(Component.translatable("gui.buildcraft.empty"), amount)
                    : List.of(Platform.get().fluidName(fluid), amount);
            renderComponentTooltip(poseStack, lines, mouseX, mouseY);
        }
    }
}
