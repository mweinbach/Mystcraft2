package ports.jurassicraft.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.genetics.Hybridization;

/** The original lab panels, with inventory positions shared by the server menu. */
public final class DnaMachineScreen extends AbstractContainerScreen<DnaMachineMenu> {
    private static final ResourceLocation EXTRACTOR = texture("dna_extractor");
    private static final ResourceLocation SEQUENCER = texture("dna_sequencer");
    private static final ResourceLocation COMBINATOR = texture("dna_combinator");
    private static final ResourceLocation HYBRIDIZER = texture("dna_hybridizer");
    private static final ResourceLocation SYNTHESIZER = texture("dna_synthesizer");
    private Button modeButton;
    private static ResourceLocation texture(String name) { return new ResourceLocation(PortMod.MOD_ID, "textures/gui/" + name + ".png"); }
    public DnaMachineScreen(DnaMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
    @Override protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        if (menu.kind() == DnaMachineKind.COMBINATOR) {
            modeButton = addRenderableWidget(new Button(leftPos + 128, topPos + 64, 30, 10,
                    Component.literal("<->"), button -> {
                        if (minecraft != null && minecraft.gameMode != null)
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                    }));
        }
    }
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        ResourceLocation panel = switch (menu.kind()) {
            case EXTRACTOR -> EXTRACTOR;
            case SEQUENCER -> SEQUENCER;
            case COMBINATOR -> menu.hybridizerMode() ? HYBRIDIZER : COMBINATOR;
            case SYNTHESIZER -> SYNTHESIZER;
        };
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, panel);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        switch (menu.kind()) {
            case EXTRACTOR -> blit(pose, leftPos + 77, topPos + 36, 176, 0, menu.progressScaled(0, 24) + 1, 16);
            case SEQUENCER -> {
                for (int lane = 0; lane < 3; lane++)
                    blit(pose, leftPos + 87, topPos + 21 + lane * 20, 176, 0, menu.progressScaled(lane, 22), 6);
            }
            case COMBINATOR -> {
                if (menu.hybridizerMode())
                    blit(pose, leftPos + 86, topPos + 25, 176, 0, 4, menu.progressScaled(0, 27));
                else blit(pose, leftPos + 93, topPos + 30, 176, 0, 8, menu.progressScaled(0, 24));
            }
            case SYNTHESIZER -> blit(pose, leftPos + 79, topPos + 34, 176, 14, menu.progressScaled(0, 24) + 1, 16);
        }
    }
    @Override protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
        super.renderLabels(pose, mouseX, mouseY);
        if (menu.hybridizerMode() && !Hybridization.hasRecipes())
            font.draw(pose, Component.translatable("gui.jurassicraft.dna_mode.no_hybrids"), 8, 60, 0x404040);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
        if (modeButton != null && modeButton.isHoveredOrFocused())
            renderTooltip(pose, Component.translatable(menu.hybridizerMode()
                    ? "gui.jurassicraft.dna_mode.combine" : "gui.jurassicraft.dna_mode.hybridize"), mouseX, mouseY);
        for (int i = 0; i < menu.kind().slots; i++) {
            var slot = menu.slots.get(i);
            if (slot.isActive() && !slot.hasItem() && mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                    && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                renderTooltip(pose, Component.translatable("gui.jurassicraft.dna_slot." + menu.slotRole(i)), mouseX, mouseY);
                break;
            }
        }
    }
}
