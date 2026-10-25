package dev.icbm.port.defense;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class DefenseScreen extends HandledScreen<DefenseScreenHandler> {
  private final Map<Integer, ButtonWidget> controls = new HashMap<>();

  public DefenseScreen(DefenseScreenHandler handler, PlayerInventory inventory, Text title) {
    super(handler, inventory, title);
    backgroundWidth = 264;
    backgroundHeight = 222;
    playerInventoryTitleY = 129;
  }

  @Override
  protected void init() {
    super.init();
    controls.clear();
    button(0, 8, 35, 28, "-10");
    button(1, 38, 35, 28, "+10");
    button(2, 8, 55, 28, "-10");
    button(3, 38, 55, 28, "+10");
    button(4, 8, 75, 28, "-1");
    button(5, 38, 75, 28, "+1");
    button(6, 8, 95, 82, "Explosives");
    button(7, 93, 95, 82, "Cargo");
    button(8, 8, 115, 70, "Radio");
    button(9, 81, 115, 70, "Redstone");
    button(13, 130, 75, 34, "-100");
    button(14, 166, 75, 34, "+100");
    button(11, 154, 115, 100, "Enabled");
    button(10, 174, 55, 80, "Fire EMP");
    button(12, 174, 55, 80, "Radar only");
  }

  private void button(int id, int bx, int by, int width, String label) {
    var widget =
        new ButtonWidget(
            x + bx,
            y + by,
            width,
            18,
            Text.literal(label),
            button -> {
              if (client != null && client.interactionManager != null)
                client.interactionManager.clickButton(handler.syncId, id);
            });
    controls.put(id, widget);
    addDrawableChild(widget);
  }

  private void toggle(int id, String label, int property) {
    controls
        .get(id)
        .setMessage(Text.literal(label + ": " + (handler.value(property) == 0 ? "off" : "on")));
  }

  private void updateControls() {
    int kind = handler.value(10);
    controls.get(2).visible = kind == 0;
    controls.get(3).visible = kind == 0;
    controls.get(9).visible = kind == 0;
    controls.get(10).visible = kind == 1;
    controls.get(12).visible = kind == 2;
    controls.get(10).active = handler.value(7) == 0 && handler.value(12) != 0;
    toggle(6, "Explosive", 3);
    toggle(7, "Cargo", 4);
    toggle(8, "Radio", 5);
    toggle(9, "Redstone", 6);
    toggle(11, "Enabled", 12);
    toggle(12, "Radar", 14);
  }

  @Override
  protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
    fill(matrices, x, y, x + backgroundWidth, y + backgroundHeight, 0xff252a33);
    for (var slot : handler.slots) {
      int sx = x + slot.x, sy = y + slot.y;
      fill(matrices, sx - 1, sy - 1, sx + 17, sy + 17, 0xff11151b);
      fill(matrices, sx, sy, sx + 16, sy + 16, 0xff565d68);
      fill(matrices, sx + 1, sy + 1, sx + 15, sy + 15, 0xff343b46);
      fill(matrices, sx, sy + 16, sx + 17, sy + 17, 0xff8993a2);
    }
  }

  @Override
  protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
    textRenderer.draw(matrices, title, 8, 7, 0xffffff);
    textRenderer.draw(
        matrices,
        "Energy: " + handler.value(9) + " / " + handler.value(17) + " kE",
        8,
        22,
        0xb4d7ff);
    if (handler.value(10) == 1)
      textRenderer.draw(
          matrices,
          "Shot: " + handler.value(19) + " kE, upkeep " + handler.value(18) + "/t",
          120,
          22,
          0xb4d7ff);
    textRenderer.draw(
        matrices, "Range: " + handler.value(0) + " / " + handler.value(11), 72, 40, 0xffffff);
    if (handler.value(10) == 0)
      textRenderer.draw(matrices, "Trigger: " + handler.value(1), 72, 60, 0xffffff);
    else if (handler.value(10) == 1)
      textRenderer.draw(matrices, "Boosters: " + handler.value(13), 8, 60, 0xffffff);
    else textRenderer.draw(matrices, "Cooldown: " + handler.value(7), 8, 60, 0xffffff);
    textRenderer.draw(matrices, "Channel: " + handler.value(2), 72, 80, 0xffffff);
    if (handler.value(10) == 0) {
      String[] states = {"Idle", "Off", "Contact", "Incoming"};
      int state = Math.floorMod(handler.value(15), states.length);
      textRenderer.draw(matrices, "State: " + states[state], 178, 40, 0xffffff);
      textRenderer.draw(
          matrices,
          "Tracks: " + handler.value(8) + " (" + handler.value(16) + " in)",
          178,
          60,
          0xffffff);
    } else textRenderer.draw(matrices, "Wait: " + handler.value(7), 178, 40, 0xffffff);
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    updateControls();
    renderBackground(matrices);
    super.render(matrices, mouseX, mouseY, delta);
    drawMouseoverTooltip(matrices, mouseX, mouseY);
  }
}
