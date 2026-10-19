package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.modifier.LinkModifierMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Classic single-slot admin editor. Controls intentionally never accept destination coordinates. */
public final class LinkModifierScreen extends AbstractContainerScreen<LinkModifierMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("mystcraft", "textures/gui/single_slot.png");
    private static final String[] LABELS = {"I", "O", "P", "M", "D", "R"};
    private EditBox titleField, seedField;
    private final Button[] flags = new Button[6];
    private boolean syncing;
    public LinkModifierScreen(LinkModifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 166; inventoryLabelY = 72;
    }
    @Override protected void init() {
        super.init();
        seedField = new EditBox(font, leftPos + 80, topPos + 15, 87, 14, Component.translatable("gui.mystcraft.modifier.seed"));
        titleField = new EditBox(font, leftPos + 80, topPos + 56, 87, 14, Component.translatable("gui.mystcraft.modifier.title"));
        seedField.setMaxLength(21); titleField.setMaxLength(21);
        seedField.setValue(menu.seed()); titleField.setValue(menu.title());
        seedField.setResponder(value -> text(value, LinkModifierMenu.SEED_CLEAR, LinkModifierMenu.SEED_CHAR, LinkModifierMenu.SEED_APPLY));
        titleField.setResponder(value -> text(value, LinkModifierMenu.TITLE_CLEAR, LinkModifierMenu.TITLE_CHAR, LinkModifierMenu.TITLE_APPLY));
        addRenderableWidget(seedField); addRenderableWidget(titleField);
        for (int i = 0; i < flags.length; i++) {
            final int index = i;
            flags[i] = addRenderableWidget(new Button(leftPos + 5 + i / 3 * 20, topPos + 10 + i % 3 * 20, 18, 18,
                    Component.literal(LABELS[i]), button -> send(LinkModifierMenu.FLAG_START + index)));
        }
    }
    private void send(int command) { if (minecraft != null && minecraft.gameMode != null) net.mystcraft.integration.network.MenuActions.send(menu.containerId, command); }
    private void text(String value, int clear, int character, int apply) {
        if (syncing) return;
        send(clear);
        for (int i = 0; i < value.length(); i++) send(character | value.charAt(i));
        send(apply);
    }
    @Override protected void containerTick() {
        super.containerTick();
        titleField.tick(); seedField.tick();
        syncing = true;
        if (!titleField.isFocused() && !titleField.getValue().equals(menu.title())) titleField.setValue(menu.title());
        if (!seedField.isFocused() && !seedField.getValue().equals(menu.seed())) seedField.setValue(menu.seed());
        syncing = false;
        titleField.setEditable(!menu.book().isEmpty());
        seedField.setEditable(menu.seedEditable());
        for (int i = 0; i < flags.length; i++) {
            flags[i].active = !menu.book().isEmpty();
            flags[i].setMessage(Component.literal(LABELS[i]).withStyle(menu.flag(i) ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
    }
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
    @Override protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
        font.draw(pose, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040);
        font.draw(pose, Component.translatable("gui.mystcraft.modifier.seed"), 48, 18, 0x404040);
        font.draw(pose, Component.translatable("gui.mystcraft.modifier.title"), 48, 59, 0x404040);
        String destination = LinkDestination.read(menu.book()).map(link -> link.dimension().location().toString()).orElse("");
        font.draw(pose, font.plainSubstrByWidth(destination, 65), 101, 39, 0x404040);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != 256 && (titleField.isFocused() || seedField.isFocused())) {
            EditBox field = titleField.isFocused() ? titleField : seedField;
            return field.keyPressed(key, scanCode, modifiers) || field.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose); super.render(pose, mouseX, mouseY, partialTick); renderTooltip(pose, mouseX, mouseY);
        for (int i = 0; i < flags.length; i++) if (flags[i].isHoveredOrFocused())
            renderTooltip(pose, Component.literal(LinkModifierMenu.FLAGS.get(i) + (menu.flag(i) ? " ✓" : "")), mouseX, mouseY);
        if (mouseX >= leftPos + 80 && mouseX < leftPos + 167 && mouseY >= topPos + 15 && mouseY < topPos + 29 && !menu.seedEditable())
            renderTooltip(pose, Component.translatable("gui.mystcraft.modifier.seed_unallocated"), mouseX, mouseY);
    }
}
