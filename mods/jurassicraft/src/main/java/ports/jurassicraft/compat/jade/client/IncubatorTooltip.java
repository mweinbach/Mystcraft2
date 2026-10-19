package ports.jurassicraft.compat.jade.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.compat.jade.ServerProviders;
import ports.jurassicraft.hatching.HatcheryRegistry;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

public enum IncubatorTooltip implements IBlockComponentProvider {
    INSTANCE;
    @Override public ResourceLocation getUid() { return ServerProviders.INCUBATOR_ID; }
    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(ServerProviders.INCUBATOR_DATA, Tag.TAG_COMPOUND)) return;
        ListTag eggs = accessor.getServerData().getCompound(ServerProviders.INCUBATOR_DATA).getList("Eggs", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(5, eggs.size()); i++) {
            CompoundTag egg = eggs.getCompound(i);
            int slot = egg.getInt("Slot");
            ItemStack stack = ItemStack.of(egg.getCompound("Egg"));
            if (slot < 0 || slot >= 5 || stack.isEmpty()) continue;
            tooltip.add(Component.translatable("tooltip.jurassicraft.jade.slot", slot + 1).withStyle(ChatFormatting.GOLD));
            tooltip.append(tooltip.getElementHelper().smallItem(stack));
            tooltip.append(new EggProgress(egg.getInt("Progress"), egg.getInt("Temperature") > 50));
        }
    }
    private static final class EggProgress extends Element {
        private static final ResourceLocation TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/gui/incubator_waila.png");
        private final int progress;
        private final boolean male;
        EggProgress(int progress, boolean male) {
            this.progress = Mth.clamp(progress, 0, HatcheryRegistry.Machine.INCUBATOR.ticks); this.male = male;
        }
        @Override public Vec2 getSize() { return new Vec2(14, 10); }
        @Override public Component getMessage() {
            return Component.translatable("tooltip.jurassicraft.jade.egg_narration",
                    progress * 100 / HatcheryRegistry.Machine.INCUBATOR.ticks,
                    Component.translatable("mode.jurassicraft." + (male ? "male" : "female")));
        }
        @Override public void render(PoseStack pose, float x, float y, float maxX, float maxY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, TEXTURE);
            int pixels = progress * 14 / HatcheryRegistry.Machine.INCUBATOR.ticks;
            GuiComponent.blit(pose, (int) x, (int) y + 2, 0, 0, 14, 5, 14, 15);
            if (pixels > 0) GuiComponent.blit(pose, (int) x, (int) y + 2, 0, male ? 10 : 5, pixels, 5, 14, 15);
        }
    }
}
