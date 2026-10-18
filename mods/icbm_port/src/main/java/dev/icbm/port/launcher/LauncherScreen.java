package dev.icbm.port.launcher;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public final class LauncherScreen extends HandledScreen<LauncherScreenHandler> {
  private TextFieldWidget xField, yField, zField, frequency;
  private boolean targeted, received;
  private ButtonWidget modeButton;

  public LauncherScreen(LauncherScreenHandler handler, PlayerInventory inventory, Text title) {
    super(handler, inventory, title);
    backgroundHeight = 248;
    playerInventoryTitleY = 154;
  }

  @Override
  protected void init() {
    super.init();
    xField = field(8, 66, 50, "X");
    yField = field(62, 66, 50, "Y");
    zField = field(116, 66, 50, "Z");
    frequency = field(80, 94, 80, "Frequency");
    xField.setText(Integer.toString(coordinate(0)));
    yField.setText(Integer.toString(coordinate(1)));
    zField.setText(Integer.toString(coordinate(2)));
    frequency.setText(Integer.toString(handler.properties.get(5)));
    targeted = handler.properties.get(4) != 0;
    modeButton =
        addDrawableChild(
            new ButtonWidget(
                x + 8,
                y + 116,
                76,
                20,
                modeText(),
                button -> {
                  targeted = !targeted;
                  button.setMessage(modeText());
                }));
    addDrawableChild(
        new ButtonWidget(x + 88, y + 116, 80, 20, Text.literal("Save target"), button -> send(0)));
    addDrawableChild(
        new ButtonWidget(x + 8, y + 138, 76, 20, Text.literal("Charge"), button -> send(1)));
    addDrawableChild(
        new ButtonWidget(x + 88, y + 138, 80, 20, Text.literal("Launch"), button -> send(2)));
  }

  private int coordinate(int index) {
    return (handler.properties.get(index) & 65535) | (handler.properties.get(index + 6) << 16);
  }

  private Text modeText() {
    return Text.literal(targeted ? "Target arc" : "Direct fire");
  }

  private TextFieldWidget field(int left, int top, int width, String name) {
    TextFieldWidget field =
        new TextFieldWidget(textRenderer, x + left, y + top, width, 18, Text.literal(name));
    field.setMaxLength(10);
    addDrawableChild(field);
    return field;
  }

  private void send(int action) {
    try {
      BlockPos target =
          new BlockPos(
              Integer.parseInt(xField.getText()),
              Integer.parseInt(yField.getText()),
              Integer.parseInt(zField.getText()));
      PacketByteBuf buf = PacketByteBufs.create();
      buf.writeInt(handler.syncId);
      buf.writeBlockPos(target);
      buf.writeBoolean(targeted);
      buf.writeInt(Integer.parseInt(frequency.getText()));
      buf.writeInt(action);
      buf.writeInt(0);
      ClientPlayNetworking.send(LauncherContent.CONFIGURE, buf);
    } catch (NumberFormatException ignored) {
    }
  }

  @Override
  protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
    fill(matrices, x, y, x + backgroundWidth, y + backgroundHeight, 0xffc6c6c6);
    fill(matrices, x + 25, y + 27, x + 43, y + 45, 0xff777777);
    fill(matrices, x + 61, y + 27, x + 79, y + 45, 0xff777777);
  }

  @Override
  protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
    super.drawForeground(matrices, mouseX, mouseY);
    textRenderer.draw(matrices, "Missile   Redstone", 8, 17, 0x404040);
    textRenderer.draw(
        matrices,
        "Energy "
            + (handler.properties.get(3) & 65535)
            + " / "
            + (handler.properties.get(10) & 65535),
        8,
        49,
        0x404040);
    textRenderer.draw(matrices, "Frequency", 8, 99, 0x404040);
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    if (!received && handler.properties.get(9) == 1) {
      received = true;
      xField.setText(Integer.toString(coordinate(0)));
      yField.setText(Integer.toString(coordinate(1)));
      zField.setText(Integer.toString(coordinate(2)));
      frequency.setText(Integer.toString(handler.properties.get(5)));
      targeted = handler.properties.get(4) != 0;
      modeButton.setMessage(modeText());
    }
    renderBackground(matrices);
    super.render(matrices, mouseX, mouseY, delta);
    drawMouseoverTooltip(matrices, mouseX, mouseY);
  }
}
