package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.core.PaintbrushItem;
import ports.buildcraft.robotics.ZonePackets;
import ports.buildcraft.robotics.ZonePlannerBlockEntity;
import ports.buildcraft.robotics.ZonePlannerMenu;
import ports.buildcraft.robotics.zone.ZonePlan;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;

/**
 * Upstream {@code GuiZonePlanner}. Dragging with an empty hand pans the map and the wheel zooms it; dragging with a
 * coloured paintbrush paints or erases that colour's layer. Painting and panning start on the map, so the slots
 * beside it still take clicks.
 */
public final class ZonePlannerScreen extends BCScreen<ZonePlannerMenu> {
    private static final int MAP_X = 8, MAP_Y = 9, MAP_WIDTH = 213, MAP_HEIGHT = 100;
    private static final int INPUT_X = 44, INPUT_Y = 128, INPUT_U = 9, INPUT_V = 228, INPUT_WIDTH = 28, INPUT_HEIGHT = 9;
    private static final int OUTPUT_X = 236, OUTPUT_Y = 45, OUTPUT_U = 0, OUTPUT_V = 228, OUTPUT_WIDTH = 9, OUTPUT_HEIGHT = 28;
    private float cameraX;
    private float cameraZ;
    private float cameraY = 256;
    private float scaleSpeed;
    private boolean panning;
    private float startMouseX, startMouseY, startX, startZ;
    @Nullable private BlockPos selectionStart;
    @Nullable private ZonePlan buffer;
    @Nullable private BlockPos lastSelected;

    public ZonePlannerScreen(ZonePlannerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/zone_planner.png"), 256, 228);
        ZonePlannerBlockEntity planner = planner();
        if (planner != null) {
            cameraX = planner.getBlockPos().getX();
            cameraZ = planner.getBlockPos().getZ();
        }
    }

    @Nullable
    private ZonePlannerBlockEntity planner() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof ZonePlannerBlockEntity planner
                ? planner : null;
    }

    @Nullable
    private DyeColor brush() {
        return PaintbrushItem.colorOf(menu.getCarried());
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        ZonePlannerBlockEntity planner = planner();
        if (planner == null || minecraft == null || minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        int input = (int) Math.round(INPUT_WIDTH * Mth.clamp(planner.deltaProgressInput.getDynamic(now, partialTicks), 0, 1));
        int output = (int) Math.round(OUTPUT_HEIGHT * Mth.clamp(planner.deltaProgressOutput.getDynamic(now, partialTicks), 0, 1));
        RenderSystem.setShaderTexture(0, texture);
        if (input > 0) blit(poseStack, leftPos + INPUT_X, topPos + INPUT_Y, INPUT_U, INPUT_V, input, INPUT_HEIGHT);
        if (output > 0) blit(poseStack, leftPos + OUTPUT_X, topPos + OUTPUT_Y, OUTPUT_U, OUTPUT_V, OUTPUT_WIDTH, output);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        ZonePlannerBlockEntity planner = planner();
        if (planner == null || minecraft == null || minecraft.level == null) return;
        cameraY += scaleSpeed;
        scaleSpeed *= 0.7F;
        int dimension = ZonePlannerMapChunkKey.dimensionId(minecraft.level);
        ZonePlannerMapChunk underCamera = ZonePlannerMapDataClient.INSTANCE.getChunk(minecraft.level,
                new ZonePlannerMapChunkKey(new ChunkPos((int) cameraX >> 4, (int) cameraZ >> 4), dimension,
                        ZonePlannerMapRenderer.mapLevel(minecraft.level)));
        if (underCamera != null) {
            ZonePlannerMapChunk.Column data = underCamera.column((int) cameraX, (int) cameraZ);
            if (data != null && data.y() + 10 > cameraY) cameraY = Math.max(cameraY, data.y() + 10);
        }
        DyeColor brush = brush();
        ZonePlan[] painted = null;
        if (brush != null && buffer != null) {
            painted = new ZonePlan[ZonePlannerBlockEntity.LAYERS];
            painted[brush.getId()] = buffer;
        }
        lastSelected = ZonePlannerMapRenderer.render(planner, leftPos, topPos, cameraX, cameraY, cameraZ, mouseX, mouseY, brush, painted);
        if (lastSelected != null) {
            font.draw(poseStack, "X: " + lastSelected.getX() + " Y: " + lastSelected.getY() + " Z: " + lastSelected.getZ(), 130, 130, 0x404040);
        }
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= leftPos + MAP_X && mouseX < leftPos + MAP_X + MAP_WIDTH && mouseY >= topPos + MAP_Y && mouseY < topPos + MAP_Y + MAP_HEIGHT;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Upstream's wheel reports about 120 per notch; a 1.19 notch is one unit, and 120 / 30 is 4.
        scaleSpeed -= (float) delta * 4;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        panning = false;
        selectionStart = null;
        if (!overMap(mouseX, mouseY)) return handled;
        ItemStack carried = menu.getCarried();
        if (PaintbrushItem.isPaintbrush(carried)) {
            if (lastSelected != null && PaintbrushItem.colorOf(carried) != null) selectionStart = lastSelected;
        } else if (carried.isEmpty()) {
            startX = cameraX;
            startZ = cameraZ;
            startMouseX = (float) mouseX;
            startMouseY = (float) mouseY;
            panning = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            cameraX = startX - ((float) mouseX - startMouseX) * 0.3F;
            cameraZ = startZ - ((float) mouseY - startMouseY) * 0.3F;
            return true;
        }
        ZonePlannerBlockEntity planner = planner();
        DyeColor brush = brush();
        BlockPos selected = ZonePlannerMapRenderer.ray((int) (mouseX * (minecraft == null ? 1 : minecraft.getWindow().getGuiScale())),
                minecraft == null ? 0 : (int) (minecraft.getWindow().getHeight() - mouseY * minecraft.getWindow().getGuiScale()));
        if (selected != null) lastSelected = selected;
        if (planner != null && brush != null && selectionStart != null && lastSelected != null && (button == 0 || button == 1)) {
            buffer = new ZonePlan(planner.layers[brush.getId()]);
            BlockPos origin = planner.getBlockPos();
            buffer.paintRectangle(selectionStart.getX() - origin.getX(), selectionStart.getZ() - origin.getZ(),
                    lastSelected.getX() - origin.getX(), lastSelected.getZ() - origin.getZ(), button == 0);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        DyeColor brush = brush();
        ZonePlannerBlockEntity planner = planner();
        if (brush != null && buffer != null && planner != null) {
            planner.layers[brush.getId()] = buffer;
            ZonePlan plan = buffer;
            int index = brush.getId();
            ClientPlatform.get().sendToServer(ZonePackets.PLAN_CHANNEL, payload -> {
                payload.writeBlockPos(planner.getBlockPos());
                payload.writeShort(index);
                plan.write(payload);
            });
        }
        selectionStart = null;
        buffer = null;
        panning = false;
        return handled;
    }
}
