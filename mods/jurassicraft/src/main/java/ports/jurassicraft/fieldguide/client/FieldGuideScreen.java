package ports.jurassicraft.fieldguide.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.fieldguide.FieldGuideNetwork;

/** A compact, translated field manual with an optional animal examination spread. */
public final class FieldGuideScreen extends Screen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("jurassicraft", "textures/field_guide/background.png");
    private static final String PREFIX = "gui.jurassicraft.field_guide.";
    private static final List<String> SPREADS = List.of("introduction", "fossils", "nests", "laboratory", "synthesis", "hatching", "coelacanth", "plants", "care", "orders", "breeding", "park", "tours", "collecting");
    private static final int LORE_WIDTH = 103, LORE_LINES = 12;
    private final DinosaurEntity dinosaur;
    private final FieldGuideNetwork.Snapshot snapshot;
    private int spread;
    private Button previous;
    private Button next;

    public FieldGuideScreen(@Nullable DinosaurEntity dinosaur, @Nullable FieldGuideNetwork.Snapshot snapshot) {
        super(Component.translatable(PREFIX + "title"));
        this.dinosaur = dinosaur;
        this.snapshot = dinosaur == null ? null : Objects.requireNonNull(snapshot, "dinosaur inspection snapshot");
    }

    private int loreSpreadCount() {
        if (dinosaur == null) return 0;
        return Math.max(1, (font.split(description(), LORE_WIDTH).size() + LORE_LINES * 2 - 1) / (LORE_LINES * 2));
    }
    private int spreadCount() { return SPREADS.size() + (dinosaur == null ? 0 : 1 + loreSpreadCount()); }
    private boolean examining() { return dinosaur != null && spread == 0; }
    private boolean lore() { return dinosaur != null && spread > 0 && spread <= loreSpreadCount(); }
    private String manualKey() { return SPREADS.get(spread - (dinosaur == null ? 0 : 1 + loreSpreadCount())); }
    private Component section(String side, String part) {
        return Component.translatable(PREFIX + manualKey() + "." + side + "." + part);
    }

    @Override protected void init() {
        int x = (width - 256) / 2;
        int y = (height - 202) / 2;
        previous = addRenderableWidget(new Button(x + 18, y + 180, 72, 20,
                Component.translatable(PREFIX + "previous"), button -> changeSpread(-1)));
        next = addRenderableWidget(new Button(x + 166, y + 180, 72, 20,
                Component.translatable(PREFIX + "next"), button -> changeSpread(1)));
        updateButtons();
    }

    private void updateButtons() {
        previous.active = spread > 0;
        next.active = spread + 1 < spreadCount();
    }

    private void changeSpread(int direction) {
        int nextSpread = Math.max(0, Math.min(spreadCount() - 1, spread + direction));
        if (nextSpread != spread) {
            spread = nextSpread;
            updateButtons();
            triggerImmediateNarration(true);
        }
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) { changeSpread(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) { changeSpread(1); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void title(PoseStack pose, Component text, int x, int y) {
        // Titles can be longer in translations, so use the same width as the body.
        drawText(pose, text, x, y, 112, 20, 0x402c1c);
    }

    private void drawText(PoseStack pose, Component text, int x, int y, int width, int height, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        float scale = Math.min(1F, (float) height / Math.max(1, lines.size() * 10));
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) font.draw(pose, lines.get(i), 0, i * 10, color);
        pose.popPose();
    }

    private Component description() {
        return Component.translatable(PREFIX + "species." + dinosaur.species().id());
    }

    private Component status() {
        Component foodState = Component.translatable(PREFIX + (snapshot.hungry() ? "hungry" : "fed"));
        Component waterState = Component.translatable(PREFIX + (snapshot.thirsty() ? "thirsty" : "hydrated"));
        Component food = Component.translatable(PREFIX + "hunger_detail", snapshot.foodPercent(), foodState);
        Component water = Component.translatable(PREFIX + "thirst_detail", snapshot.waterPercent(), waterState);
        return Component.translatable(PREFIX + "health", Math.round(snapshot.health()), Math.round(snapshot.maxHealth()))
                .append("\n").append(Component.translatable(PREFIX + "growth", (int) (dinosaur.growth() * 100)))
                .append("\n").append(Component.translatable(PREFIX + "stage_sex",
                        Component.translatable(PREFIX + "stage_" + FieldGuideNetwork.Snapshot.growthStage(dinosaur.growth())),
                        Component.translatable(PREFIX + (dinosaur.isMale() ? "male" : "female"))))
                .append("\n").append(Component.translatable(PREFIX + "days_lived", snapshot.daysLived()))
                .append("\n").append(food).append("\n").append(water);
    }

    private Component acceptedFood() {
        var foods = new java.util.ArrayList<Component>();
        if (snapshot.acceptsMeat()) foods.add(Component.translatable(PREFIX + "diet_meat"));
        if (snapshot.acceptsPlants()) foods.add(Component.translatable(PREFIX + "diet_plants"));
        if (snapshot.acceptsFish()) foods.add(Component.translatable(PREFIX + "diet_fish"));
        if (snapshot.acceptsInsects()) foods.add(Component.translatable(PREFIX + "diet_insects"));
        if (snapshot.acceptsKrill()) foods.add(Component.translatable(PREFIX + "diet_krill"));
        if (snapshot.acceptsPlankton()) foods.add(Component.translatable(PREFIX + "diet_plankton"));
        var result = Component.empty();
        for (var food : foods) {
            if (!result.getString().isEmpty()) result.append(", ");
            result.append(food);
        }
        return foods.isEmpty() ? Component.translatable(PREFIX + "diet_none") : result;
    }

    private Component behavior() {
        String state = dinosaur.isCarcass() ? "state_carcass" : dinosaur.isTranquilized() ? "state_tranquilized"
                : dinosaur.isNaturallySleeping() ? "state_sleeping" : "state_awake";
        boolean imprinted = dinosaur.getOwnerUUID() != null;
        var result = Component.translatable(PREFIX + "diet", acceptedFood())
                .append("\n").append(Component.translatable(PREFIX + "state", Component.translatable(PREFIX + state)))
                .append("\n").append(Component.translatable(PREFIX + (imprinted ? "imprinted" : "wild")));
        if (imprinted) result.append("\n").append(Component.translatable(PREFIX + "order",
                Component.translatable(PREFIX + "order_" + dinosaur.getOrder().name().toLowerCase(Locale.ROOT))));
        if (snapshot.lowHealth()) result.append("\n").append(Component.translatable(PREFIX + "warning_low_health"));
        if (snapshot.poisoned()) result.append("\n").append(Component.translatable(PREFIX + "warning_poisoned"));
        if (snapshot.lowAir()) result.append("\n").append(Component.translatable(PREFIX + "warning_low_air"));
        return result;
    }

    @Override public Component getNarrationMessage() {
        if (examining()) return Component.empty().append(title).append(". ").append(dinosaur.getName()).append(". ")
                .append(status()).append(". ").append(behavior());
        if (lore()) {
            var lines = font.split(description(), LORE_WIDTH);
            int first = (spread - 1) * LORE_LINES * 2;
            var result = Component.empty().append(title).append(". ").append(dinosaur.getName()).append(". ");
            for (int i = first; i < Math.min(first + LORE_LINES * 2, lines.size()); i++) {
                StringBuilder line = new StringBuilder();
                lines.get(i).accept((index, style, codepoint) -> { line.appendCodePoint(codepoint); return true; });
                result.append(line.toString()).append(" ");
            }
            return result;
        }
        return Component.empty().append(title).append(". ").append(section("left", "title")).append(". ")
                .append(section("left", "body")).append(". ").append(section("right", "title")).append(". ")
                .append(section("right", "body"));
    }

    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        int x = (width - 256) / 2;
        int y = (height - 202) / 2;
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, BACKGROUND);
        blit(pose, x, y, 0, 0, 256, 177);
        if (examining()) {
            title(pose, dinosaur.getName(), x + 16, y + 12);
            drawText(pose, status(), x + 16, y + 34, 108, 62, 0x36291d);
            // Size the live model to the available page, even for large adults.
            float dimension = Math.max(dinosaur.getBbHeight(), dinosaur.getBbWidth());
            int scale = Math.max(1, Math.min(30, (int) (48 / Math.max(dimension, 0.1F))));
            InventoryScreen.renderEntityInInventory(x + 69, y + 157, scale, 18, 5, dinosaur);
            title(pose, Component.translatable(PREFIX + "about_species"), x + 140, y + 12);
            drawText(pose, behavior(), x + 140, y + 34, 103, 125, 0x36291d);
        } else if (lore()) {
            title(pose, dinosaur.getName(), x + 16, y + 12);
            title(pose, Component.translatable(PREFIX + "field_notes"), x + 140, y + 12);
            var lines = font.split(description(), LORE_WIDTH);
            int first = (spread - 1) * LORE_LINES * 2;
            for (int i = 0; i < LORE_LINES * 2 && first + i < lines.size(); i++)
                font.draw(pose, lines.get(first + i), x + (i < LORE_LINES ? 16 : 140), y + 34 + (i % LORE_LINES) * 10, 0x36291d);
        } else {
            title(pose, section("left", "title"), x + 16, y + 12);
            drawText(pose, section("left", "body"), x + 16, y + 34, 108, 125, 0x36291d);
            title(pose, section("right", "title"), x + 140, y + 12);
            drawText(pose, section("right", "body"), x + 140, y + 34, 103, 125, 0x36291d);
        }
        drawCenteredString(pose, font, Component.translatable(PREFIX + "page", spread + 1, spreadCount()), x + 128, y + 185, 0xffffff);
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
