package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;

/**
 * Upstream {@code GuiElementStatementVariant}: a pop-up of choices around the slot it opened over, the first in
 * the slot itself and the rest in rings of 8 and 16 around it. Releasing the mouse over a choice picks it.
 */
final class SlotRing {
    static final int SLOT = 18;
    static final ResourceLocation MISC_SLOTS = BuildCraft.id("textures/gui/misc_slots.png");
    private static final int[][] OFFSET_HOVER = {
            {0, 0},
            {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0},
            {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {2, -1}, {2, 0}, {2, 1},
            {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2}, {-2, 1}, {-2, 0}, {-2, -1}};

    @FunctionalInterface
    interface Drawer {
        void draw(PoseStack poseStack, Object option, int x, int y);
    }

    private final int x, y, size;
    private final Object[] options;
    private final int[][] positions;
    private final Predicate<Object> canSet;
    private final Consumer<Object> select;

    /** @param possible the choices, of which null entries take a place but are not drawn or picked */
    SlotRing(int x, int y, List<?> possible, Predicate<Object> canSet, Consumer<Object> select) {
        this.x = x;
        this.y = y;
        this.canSet = canSet;
        this.select = select;
        int count = Math.min(OFFSET_HOVER.length, possible.size());
        options = possible.subList(0, count).toArray();
        positions = new int[count][];
        for (int i = 0; i < count; i++) positions[i] = new int[] {x + OFFSET_HOVER[i][0] * SLOT, y + OFFSET_HOVER[i][1] * SLOT};
        size = 8 + SLOT * (count > 9 ? 3 : 1) + SLOT * 2;
    }

    void render(PoseStack poseStack, Drawer drawer) {
        RenderSystem.setShaderTexture(0, MISC_SLOTS);
        int offset = -SLOT * (options.length > 9 ? 2 : 1) - 4;
        Ledger.nineSlice(poseStack, x + offset, y + offset, size, size, 18, 18, 36, 8, 256);
        for (int i = 0; i < options.length; i++) {
            if (options[i] != null) drawer.draw(poseStack, options[i], positions[i][0], positions[i][1]);
        }
    }

    @Nullable
    Object optionAt(double mouseX, double mouseY) {
        for (int i = 0; i < options.length; i++) {
            int[] at = positions[i];
            if (options[i] != null && mouseX >= at[0] && mouseY >= at[1] && mouseX < at[0] + SLOT && mouseY < at[1] + SLOT) return options[i];
        }
        return null;
    }

    void release(double mouseX, double mouseY) {
        Object option = optionAt(mouseX, mouseY);
        if (option != null && canSet.test(option)) select.accept(option);
    }
}
