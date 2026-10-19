package ports.jurassicraft.decor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.decor.ParkSignNetwork;
import ports.jurassicraft.decor.ParkSigns;

/** The original 5-by-3 dinosaur selector, with server-authoritative placement. */
@OnlyIn(Dist.CLIENT)
public final class PaddockSignScreen extends Screen {
    private static final int COLUMNS = 5, PER_PAGE = 15, CELL = 60;
    private static final List<String> OPTIONS = ParkSigns.PADDOCKS.stream().sorted().toList();
    private final BlockPos pos;
    private final Direction side;
    private final InteractionHand hand;
    private int page;
    private Button previous, next;
    private PaddockSignScreen(BlockPos pos, Direction side, InteractionHand hand) {
        super(Component.translatable("item.jurassicraft.paddock_sign"));
        this.pos = pos; this.side = side; this.hand = hand;
    }
    public static void open(BlockPos pos, Direction side, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new PaddockSignScreen(pos, side, hand));
    }
    @Override protected void init() {
        int y = height / 5 + 150;
        addRenderableWidget(new Button((width - 150) / 2, y, 150, 20, Component.translatable("gui.cancel"), b -> onClose()));
        previous = addRenderableWidget(new Button(width / 2 - 105, y, 20, 20, Component.literal("<"), b -> changePage(-1)));
        next = addRenderableWidget(new Button(width / 2 + 85, y, 20, 20, Component.literal(">"), b -> changePage(1)));
        updateButtons();
    }
    private void changePage(int delta) {
        page = Math.max(0, Math.min((OPTIONS.size() - 1) / PER_PAGE, page + delta));
        updateButtons();
    }
    private void updateButtons() { previous.active = page > 0; next.active = (page + 1) * PER_PAGE < OPTIONS.size(); }
    private int iconX(int index) { return width / 2 - 140 + (index % COLUMNS) * CELL; }
    private int iconY(int index) { return height / 8 - 20 + (index / COLUMNS) * CELL; }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < OPTIONS.size(); i++) {
            int x = iconX(i), y = iconY(i);
            if (mouseX >= x && mouseX < x + 48 && mouseY >= y && mouseY < y + 48) {
                ParkSignNetwork.sendSelection(pos, side, hand, OPTIONS.get(page * PER_PAGE + i));
                onClose();
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < OPTIONS.size(); i++) {
            String id = OPTIONS.get(page * PER_PAGE + i);
            int x = iconX(i), y = iconY(i);
            if (mouseX >= x && mouseX < x + 48 && mouseY >= y && mouseY < y + 48)
                fill(pose, x - 1, y - 1, x + 49, y + 49, 0x60606060);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, new ResourceLocation(PortMod.MOD_ID, "textures/paddock/" + id + ".png"));
            pose.pushPose(); pose.translate(x, y, 0); pose.scale(3, 3, 1);
            blit(pose, 0, 0, 0, 0, 16, 16, 16, 16);
            pose.popPose();
            pose.pushPose(); pose.translate(x + 24, y + 51, 0); pose.scale(.66F, .66F, 1);
            drawCenteredString(pose, font, Component.translatable("jurassicraft.sign." + id), 0, 0, 0xFFFFFF);
            pose.popPose();
        }
        super.render(pose, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
