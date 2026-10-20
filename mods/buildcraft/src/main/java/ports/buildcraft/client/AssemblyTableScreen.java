package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.AssemblyTableMenu;
import ports.buildcraft.silicon.EnumAssemblyRecipeState;

/**
 * Upstream {@code GuiAssemblyTable}: the ingredients, the recipes they allow marked saved, saved with enough and
 * active, a progress bar for the active recipe, and upstream {@code LedgerTablePower} on the right. Past twelve
 * recipes, arrows under the list page through them.
 */
public final class AssemblyTableScreen extends LaserTableScreen<AssemblyTableMenu> {
    private static final ResourceLocation ENGINE_ACTIVE = BuildCraft.id("textures/gui/engine_active.png");
    private static final ResourceLocation ENGINE_INACTIVE = BuildCraft.id("textures/gui/engine_inactive.png");
    private static final int ENGINE_ACTIVE_FRAMES = 6;
    private static final int LEDGER_COLOUR = 0xFFD46C1F;
    private static final int LEDGER_TITLE_COLOUR = 0xFFE1C92F;
    private static final int SUB_HEADER_COLOUR = 0xFFAAAFB8;
    private static final int TEXT_COLOUR = 0xFF000000;
    private static final int LINE = 12;
    private static final int PAGER_LEFT = 116;
    private static final int PAGER_RIGHT = 170;
    private static final int PAGER_Y = 108;
    private static final int PAGER_HEIGHT = 14;
    private static final int PAGER_BUTTON_WIDTH = 9;
    private static final int PAGER_COLOUR = 0xFF404040;
    private static final int PAGER_HOVER_COLOUR = 0xFFFFFFA0;

    private final Ledger ledger = new Ledger("silicon.all_tables.power", LEDGER_COLOUR);

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/assembly_table.png"), 176, 220);
    }

    private List<Component[]> ledgerLines() {
        return List.of(
                new Component[] {Component.translatable("gui.assemblyCurrentRequired").append(":"), mj(menu.target())},
                new Component[] {Component.translatable("gui.stored").append(":"), mj(menu.power())},
                new Component[] {Component.translatable("gui.assemblyRate").append(":"),
                        Component.translatable("buildcraft.mj.flow.seconds.short", Power.formatMj(menu.averagePower() * 20))});
    }

    private static Component mj(long microJoules) {
        return Component.translatable("buildcraft.mj.static.short", Power.formatMj(microJoules));
    }

    private int ledgerMaxWidth() {
        int textWidth = font.width(Component.translatable("gui.power"));
        for (Component[] line : ledgerLines()) {
            for (Component text : line) textWidth = Math.max(textWidth, font.width(text));
        }
        return Math.max(Ledger.CLOSED_WIDTH, 2 + 16 + Ledger.GAP + textWidth) + Ledger.GAP * 2;
    }

    private int ledgerMaxHeight() {
        return Ledger.GAP + 3 + LINE * 7 + Ledger.GAP;
    }

    @Override
    protected void init() {
        super.init();
        ledger.place(leftPos + imageWidth, topPos + 5, ledgerMaxWidth(), ledgerMaxHeight());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ledger.tick(ledgerMaxWidth(), ledgerMaxHeight());
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        drawProgress(poseStack, 86, 36, 48);
        for (int i = 0; i < 12; i++) {
            EnumAssemblyRecipeState state = menu.state(i);
            int v = state == null ? -1 : switch (state) {
                case POSSIBLE -> -1;
                case SAVED -> 0;
                case SAVED_ENOUGH -> 16;
                case SAVED_ENOUGH_ACTIVE -> 32;
            };
            if (v >= 0) blit(poseStack, leftPos + 116 + i % 3 * 18, topPos + 36 + i / 3 * 18, 176, v, 16, 16);
        }
        renderLedger(poseStack, partialTicks);
    }

    private void renderLedger(PoseStack poseStack, float partialTicks) {
        ledger.render(poseStack, partialTicks, () -> {
            boolean active = menu.averagePower() > 0;
            RenderSystem.setShaderTexture(0, active ? ENGINE_ACTIVE : ENGINE_INACTIVE);
            int frame = active && minecraft.level != null ? (int) (minecraft.level.getGameTime() % ENGINE_ACTIVE_FRAMES) : 0;
            int frames = active ? ENGINE_ACTIVE_FRAMES : 1;
            blit(poseStack, ledger.iconX(), ledger.iconY(), 16, 16, 0, frame * 16, 16, 16, 16, 16 * frames);
            if (!ledger.isExpanded()) return;
            int x = ledger.innerX();
            int y = ledger.iconY() + 3;
            font.drawShadow(poseStack, Component.translatable("gui.power"), x, y, LEDGER_TITLE_COLOUR);
            for (Component[] line : ledgerLines()) {
                y += LINE;
                font.drawShadow(poseStack, line[0], x, y, SUB_HEADER_COLOUR);
                y += LINE;
                font.draw(poseStack, line[1], x, y, TEXT_COLOUR);
            }
        });
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return !ledger.contains(mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ledger.contains(mouseX, mouseY)) {
            ledger.toggle();
            return true;
        }
        if (button == 0 && menu.pages() > 1) {
            int pageButton = pageButtonAt(mouseX, mouseY);
            if (pageButton >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, pageButton);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int pageButtonAt(double mouseX, double mouseY) {
        double y = mouseY - topPos;
        if (y < PAGER_Y || y >= PAGER_Y + PAGER_HEIGHT) return -1;
        double x = mouseX - leftPos;
        if (x >= PAGER_LEFT && x < PAGER_LEFT + PAGER_BUTTON_WIDTH) return AssemblyTableMenu.BUTTON_PREVIOUS_PAGE;
        if (x >= PAGER_RIGHT - PAGER_BUTTON_WIDTH && x < PAGER_RIGHT) return AssemblyTableMenu.BUTTON_NEXT_PAGE;
        return -1;
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        drawTitle(poseStack, 15);
        if (menu.pages() > 1) renderPager(poseStack, mouseX, mouseY);
    }

    private void renderPager(PoseStack poseStack, int mouseX, int mouseY) {
        int hovered = pageButtonAt(mouseX, mouseY);
        int textY = PAGER_Y + (PAGER_HEIGHT - font.lineHeight) / 2 + 1;
        font.draw(poseStack, "<", PAGER_LEFT + 2, textY,
                hovered == AssemblyTableMenu.BUTTON_PREVIOUS_PAGE ? PAGER_HOVER_COLOUR : PAGER_COLOUR);
        font.draw(poseStack, ">", PAGER_RIGHT - PAGER_BUTTON_WIDTH + 2, textY,
                hovered == AssemblyTableMenu.BUTTON_NEXT_PAGE ? PAGER_HOVER_COLOUR : PAGER_COLOUR);
        String label = (menu.page() + 1) + "/" + menu.pages();
        font.draw(poseStack, label, (PAGER_LEFT + PAGER_RIGHT - font.width(label)) / 2f, textY, PAGER_COLOUR);
    }
}
