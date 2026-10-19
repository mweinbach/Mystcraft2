package ports.jurassicraft.compat.jade.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.compat.jade.ServerProviders;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

public enum FeederTooltip implements IBlockComponentProvider {
    INSTANCE;
    @Override public ResourceLocation getUid() { return ServerProviders.FEEDER_ID; }
    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(ServerProviders.FEEDER_DATA, Tag.TAG_COMPOUND)) return;
        CompoundTag data = accessor.getServerData().getCompound(ServerProviders.FEEDER_DATA);
        tooltip.add(Component.translatable("tooltip.jurassicraft.jade.fullness").withStyle(ChatFormatting.GOLD));
        tooltip.add(new FoodIcons(data.getInt("Meat"), data.getInt("Plants")));
    }
    /** The original pair of ten-icon fullness meters, one row each for meat and plants. */
    private static final class FoodIcons extends Element {
        private static final ResourceLocation ICONS = new ResourceLocation(PortMod.MOD_ID, "textures/gui/icons.png");
        private final int meat, plants;
        FoodIcons(int meat, int plants) { this.meat = Mth.clamp(meat, 0, 576); this.plants = Mth.clamp(plants, 0, 576); }
        @Override public Vec2 getSize() { return new Vec2(100, 20); }
        @Override public Component getMessage() {
            return Component.translatable("tooltip.jurassicraft.jade.food_narration", meat * 100 / 576, plants * 100 / 576);
        }
        @Override public void render(PoseStack pose, float x, float y, float maxX, float maxY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, ICONS);
            drawRow(pose, x, y, meat, 0);
            drawRow(pose, x, y + 10, plants, 10);
        }
        private static void drawRow(PoseStack pose, float x, float y, int amount, int v) {
            float icons = amount * 10F / 576F;
            for (int i = 1; i <= 10; i++) {
                int u = i <= Mth.floor(icons) ? 20 : i < icons + .5F ? 10 : 0;
                GuiComponent.blit(pose, (int) x + (i - 1) * 10, (int) y, u, v, 10, 10, 30, 20);
            }
        }
    }
}
