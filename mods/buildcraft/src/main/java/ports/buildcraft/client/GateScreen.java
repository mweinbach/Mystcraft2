package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateMenu;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.FullStatement;
import ports.buildcraft.statement.GuiSlot;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementWrapper;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code GuiGate} as laid out by {@code gate.json}: each pair's trigger and action with their
 * parameters and on lights, the toggles that group a pair with the next, and the triggers and actions the
 * gate can use on the left and right. Pressing a statement or parameter opens upstream's ring of choices;
 * shift-clicking a statement clears it; right-dragging copies a slot's value; sources are dragged in.
 */
public final class GateScreen extends BCScreen<GateMenu> {
    private static final int SLOT = SlotRing.SLOT;
    private static final int TITLE_COLOUR = 0x404040;

    private final int horizontal, vertical, triggerArgs, actionArgs, pairWidth, pairStart, slotsHeight;
    @Nullable private SlotRing ring;
    private boolean dragging;
    @Nullable private Object dragged;

    /** One clickable slot of a pair: its statement when {@code param} is -1, otherwise that parameter. */
    private record Element(int x, int y, int index, boolean action, int param) {}

    private record Connector(int x, int y, int index) {}

    @FunctionalInterface
    private interface SourceVisitor {
        void visit(@Nullable StatementWrapper slot, int x, int y);
    }

    public GateScreen(GateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/gate.png"), 176, 117 + menu.slotHeight * SLOT);
        horizontal = menu.variant.numSlots() > 4 ? 2 : 1;
        vertical = menu.variant.numSlots() / horizontal;
        triggerArgs = menu.variant.numTriggerArgs();
        actionArgs = menu.variant.numActionArgs();
        slotsHeight = vertical * SLOT;
        pairWidth = SLOT * (3 + triggerArgs + actionArgs);
        pairStart = (162 - (pairWidth + (horizontal == 2 ? pairWidth + SLOT : 0))) / 2;
    }

    @Nullable
    private GateLogic gate() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(menu.pos()) instanceof PipeBlockEntity pipe && pipe.getPluggable(menu.side) instanceof PluggableGate gate
                ? gate.logic() : null;
    }

    private static boolean in(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private void clickSound() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // Layout

    private int pairX(int column) {
        return leftPos + pairStart + 7 + column * (SLOT + pairWidth);
    }

    private int connectorOffset() {
        return SLOT * (1 + triggerArgs);
    }

    private List<Element> elements() {
        List<Element> elements = new ArrayList<>();
        for (int row = 0; row < vertical; row++) {
            for (int column = 0; column < horizontal; column++) {
                int x = pairX(column), y = topPos + 16 + row * SLOT, index = row + column * vertical;
                elements.add(new Element(x, y, index, false, -1));
                for (int p = 0; p < triggerArgs; p++) elements.add(new Element(x + SLOT * (p + 1), y, index, false, p));
                int actionX = x + SLOT * (2 + triggerArgs);
                elements.add(new Element(actionX, y, index, true, -1));
                for (int p = 0; p < actionArgs; p++) elements.add(new Element(actionX + SLOT * (p + 1), y, index, true, p));
            }
        }
        return elements;
    }

    private List<Connector> connectors() {
        List<Connector> connectors = new ArrayList<>();
        for (int row = 0; row < vertical - 1; row++) {
            for (int column = 0; column < horizontal; column++) {
                connectors.add(new Connector(pairX(column) + connectorOffset(), topPos + 16 + 9 + row * SLOT, row + column * vertical));
            }
        }
        return connectors;
    }

    @Nullable
    private Element elementAt(double mouseX, double mouseY) {
        for (Element element : elements()) {
            if (in(mouseX, mouseY, element.x, element.y, SLOT, SLOT)) return element;
        }
        return null;
    }

    private static <S extends StatementWrapper> int sourceWidth(List<GateMenu.Group<S>> groups) {
        int width = 0;
        for (GateMenu.Group<S> group : groups) width = Math.max(width, group.values().size());
        return Math.min(4, width) * SLOT;
    }

    private static <S extends StatementWrapper> int sourceHeight(List<GateMenu.Group<S>> groups) {
        int rows = 0;
        for (GateMenu.Group<S> group : groups) rows += (group.values().size() + 3) / 4;
        return rows * SLOT + 4;
    }

    private int sourceX(boolean left) {
        return left ? leftPos - sourceWidth(menu.triggerGroups) : leftPos + imageWidth;
    }

    /** Upstream {@code GuiElementStatementSource}: rows of four, from the right on the left side, a new row per group. */
    private <S extends StatementWrapper> void forEachSource(List<GateMenu.Group<S>> groups, boolean left, SourceVisitor visitor) {
        int originX = sourceX(left), originY = topPos + 5;
        int dx = left ? -1 : 1, sx = left ? 3 : 0, ex = sx + dx * 4;
        int x = sx, y = 0;
        for (GateMenu.Group<S> group : groups) {
            int visited = 0;
            for (S slot : group.values()) {
                visitor.visit(slot, originX + x * SLOT, originY + y * SLOT);
                visited++;
                x += dx;
                if (x == ex) {
                    x = sx;
                    y++;
                }
            }
            if (visited > 0 && x != sx) {
                x = sx;
                y++;
            }
        }
    }

    private void forEachSource(SourceVisitor visitor) {
        forEachSource(menu.triggerGroups, true, visitor);
        forEachSource(menu.actionGroups, false, visitor);
    }

    @Nullable
    private Object[] sourceAt(double mouseX, double mouseY) {
        Object[] found = new Object[1];
        boolean[] hit = new boolean[1];
        forEachSource((slot, x, y) -> {
            if (in(mouseX, mouseY, x, y, SLOT, SLOT)) {
                found[0] = slot;
                hit[0] = true;
            }
        });
        return hit[0] ? found : null;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (in(mouseX, mouseY, sourceX(true), topPos + 5, sourceWidth(menu.triggerGroups), sourceHeight(menu.triggerGroups))) return false;
        if (in(mouseX, mouseY, sourceX(false), topPos + 5, sourceWidth(menu.actionGroups), sourceHeight(menu.actionGroups))) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // Statement access

    private static FullStatement<? extends StatementWrapper> statement(GateLogic gate, int index, boolean action) {
        return action ? gate.statements[index].action : gate.statements[index].trigger;
    }

    @Nullable
    private static GuiSlot valueOf(GateLogic gate, Element element) {
        if (element.index >= gate.statements.length) return null;
        FullStatement<? extends StatementWrapper> full = statement(gate, element.index, element.action);
        return element.param < 0 ? full.get() : full.get(element.param);
    }

    @Nullable
    private static <S extends StatementWrapper> S convert(FullStatement<S> full, Object value) {
        return full.convertToType(value);
    }

    private static <S extends StatementWrapper> boolean canSetStatement(FullStatement<S> full, @Nullable Object value) {
        if (value == null) return true;
        S converted = full.convertToType(value);
        return converted != null && full.canSet(converted);
    }

    /** Upstream {@code IReference.canSet} after {@code convertToType}, for the slot {@code element}. */
    private boolean canSet(Element element, @Nullable Object value) {
        GateLogic gate = gate();
        if (gate == null || element.index >= gate.statements.length) return false;
        FullStatement<? extends StatementWrapper> full = statement(gate, element.index, element.action);
        if (element.param < 0) return canSetStatement(full, value);
        return value instanceof StatementParameter parameter && full.canSet(element.param, parameter);
    }

    private void set(Element element, @Nullable Object value) {
        GateLogic gate = gate();
        if (gate == null || !canSet(element, value)) return;
        FullStatement<? extends StatementWrapper> full = statement(gate, element.index, element.action);
        if (element.param < 0) {
            StatementWrapper converted = value == null ? null : convert(full, value);
            ClientPlatform.get().sendToServer(GateMenu.CHANNEL,
                    buffer -> GateMenu.writeStatementChange(buffer, menu.containerId, element.index, element.action, converted));
        } else {
            StatementParameter parameter = (StatementParameter) value;
            ClientPlatform.get().sendToServer(GateMenu.CHANNEL,
                    buffer -> GateMenu.writeParameterChange(buffer, menu.containerId, element.index, element.action, element.param, parameter));
        }
    }

    private boolean isPossible(@Nullable Object value, boolean action) {
        if (value == null) return true;
        if (action) return value instanceof ActionWrapper wrapper && menu.isPossibleAction(wrapper);
        return value instanceof TriggerWrapper wrapper && menu.isPossibleTrigger(wrapper);
    }

    // Drawing

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);
        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, 16);
        for (int row = 0; row < vertical; row++) blit(poseStack, leftPos, topPos + 16 + row * SLOT, 0, 23, imageWidth, SLOT);
        blit(poseStack, leftPos, topPos + 16 + slotsHeight, 0, 48, imageWidth, 101);
        renderExtras(poseStack, partialTicks);
    }

    @Override
    protected void renderExtras(PoseStack poseStack, float partialTicks) {
        forEachSource((slot, x, y) -> drawGuiSlot(poseStack, slot, x, y));
        GateLogic gate = gate();
        if (gate == null) return;
        for (Element element : elements()) {
            if (element.index >= gate.statements.length) continue;
            FullStatement<? extends StatementWrapper> full = statement(gate, element.index, element.action);
            if (element.param < 0) {
                drawGuiSlot(poseStack, full.get(), element.x, element.y);
            } else if (full.get() == null || element.param >= full.get().maxParameters()) {
                RenderSystem.setShaderTexture(0, SlotRing.MISC_SLOTS);
                blit(poseStack, element.x, element.y, 0, 0, SLOT, SLOT);
                blit(poseStack, element.x, element.y, SLOT, 0, SLOT, SLOT);
            } else {
                drawGuiSlot(poseStack, full.get(element.param), element.x, element.y);
            }
        }
        RenderSystem.setShaderTexture(0, texture);
        int connector = connectorOffset();
        for (int row = 0; row < vertical; row++) {
            for (int column = 0; column < horizontal; column++) {
                int x = pairX(column), y = topPos + 16 + row * SLOT, index = row + column * vertical;
                if (index >= gate.statements.length) continue;
                boolean actionOn = menu.isActionOn(index);
                blit(poseStack, x + connector, y, 176 + (menu.isTriggerOn(index) ? 18 : 0), 18, 7, SLOT);
                blit(poseStack, x + connector + 11, y, 187 + (actionOn && gate.statements[index].action.get() != null ? 18 : 0), 18, 7, SLOT);
                blit(poseStack, x + connector + 7, y, 180 + (actionOn ? 18 : 0), 18, 4, SLOT);
            }
        }
        for (Connector link : connectors()) {
            int index = link.index;
            if (index + 1 >= gate.statements.length) continue;
            int connected = gate.connections[index] ? 18 : 0;
            blit(poseStack, link.x, link.y, 176 + (menu.isActionOn(index) ? 18 : 0), 36 + connected, SLOT, 9);
            blit(poseStack, link.x, link.y + 9, 176 + (menu.isActionOn(index + 1) ? 18 : 0), 45 + connected, SLOT, 9);
        }
    }

    /** Upstream {@code GuiElementStatementSource.drawGuiSlot}: sided statements sit on their face's colour. */
    private void drawGuiSlot(PoseStack poseStack, @Nullable GuiSlot slot, int x, int y) {
        if (slot instanceof StatementParameter parameter) {
            drawParameter(poseStack, parameter, x, y);
            return;
        }
        int v = slot instanceof StatementWrapper wrapper && wrapper.sourcePart != null ? (1 + wrapper.sourcePart.ordinal()) * SLOT : 0;
        RenderSystem.setShaderTexture(0, SlotRing.MISC_SLOTS);
        blit(poseStack, x, y, 0, v, SLOT, SLOT);
        if (slot != null) drawSprite(poseStack, slot.getSprite(), x, y);
    }

    private void drawSprite(PoseStack poseStack, @Nullable ResourceLocation sprite, int x, int y) {
        if (sprite == null) return;
        RenderSystem.setShaderTexture(0, sprite);
        blit(poseStack, x + 1, y + 1, 16, 16, 0, 0, 16, 16, 16, 16);
    }

    /** Upstream {@code ParameterRenderer}: the slot, then the parameter's sprite and stack in its draw type's order. */
    private void drawParameter(PoseStack poseStack, StatementParameter parameter, int x, int y) {
        RenderSystem.setShaderTexture(0, SlotRing.MISC_SLOTS);
        blit(poseStack, x, y, 0, 0, SLOT, SLOT);
        switch (parameter.getDrawType()) {
            case SPRITE_ONLY -> drawSprite(poseStack, parameter.getSprite(), x, y);
            case STACK_ONLY -> drawStack(poseStack, parameter, x, y, false);
            case STACK_ONLY_OR_QUESTION_MARK -> drawStack(poseStack, parameter, x, y, true);
            case SPRITE_STACK -> {
                drawSprite(poseStack, parameter.getSprite(), x, y);
                drawStack(poseStack, parameter, x, y, false);
            }
            case SPRITE_STACK_OR_QUESTION_MARK -> {
                drawSprite(poseStack, parameter.getSprite(), x, y);
                drawStack(poseStack, parameter, x, y, true);
            }
            case STACK_SPRITE -> {
                drawStack(poseStack, parameter, x, y, false);
                drawSprite(poseStack, parameter.getSprite(), x, y);
            }
            case STACK_OR_QUESTION_MARK_THEN_SPRITE -> {
                drawStack(poseStack, parameter, x, y, true);
                drawSprite(poseStack, parameter.getSprite(), x, y);
            }
        }
    }

    private void drawStack(PoseStack poseStack, StatementParameter parameter, int x, int y, boolean orQuestionMark) {
        ItemStack stack = parameter.getItemStack();
        if (!stack.isEmpty()) {
            itemRenderer.renderGuiItem(stack, x + 1, y + 1);
        } else if (orQuestionMark) {
            RenderSystem.setShaderTexture(0, SlotRing.MISC_SLOTS);
            blit(poseStack, x + 1, y + 1, 36, 0, SLOT, SLOT);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, (imageWidth - font.width(title)) / 2F, 6, TITLE_COLOUR);
        font.draw(poseStack, Component.translatable("gui.inventory"), 5, 16 + slotsHeight + 4, TITLE_COLOUR);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        super.render(poseStack, mouseX, mouseY, partialTicks);
        poseStack.pushPose();
        poseStack.translate(0, 0, 400);
        if (ring != null) ring.render(poseStack, (stack, option, x, y) -> drawGuiSlot(stack, (GuiSlot) option, x, y));
        if (dragging) renderDragged(poseStack, mouseX, mouseY);
        poseStack.popPose();
        if (ring != null) {
            if (ring.optionAt(mouseX, mouseY) instanceof GuiSlot slot) renderTooltip(poseStack, slot, mouseX, mouseY);
        } else if (!dragging && menu.getCarried().isEmpty()) {
            renderSlotTooltip(poseStack, mouseX, mouseY);
        }
    }

    private void renderTooltip(PoseStack poseStack, GuiSlot slot, int mouseX, int mouseY) {
        List<Component> tooltip = slot.getTooltip();
        if (!tooltip.isEmpty()) renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    private void renderSlotTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        Object[] source = sourceAt(mouseX, mouseY);
        if (source != null) {
            if (source[0] instanceof GuiSlot slot) renderTooltip(poseStack, slot, mouseX, mouseY);
            return;
        }
        GateLogic gate = gate();
        Element element = elementAt(mouseX, mouseY);
        if (gate == null || element == null) return;
        GuiSlot slot = valueOf(gate, element);
        if (slot != null) renderTooltip(poseStack, slot, mouseX, mouseY);
    }

    private void renderDragged(PoseStack poseStack, int mouseX, int mouseY) {
        Element target = elementAt(mouseX, mouseY);
        if (target == null || !canSet(target, dragged)) RenderSystem.setShaderColor(1.0F, 0.7F, 0.7F, 1);
        drawGuiSlot(poseStack, (GuiSlot) dragged, mouseX - 9, mouseY - 9);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    // Interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GateLogic gate = gate();
        if (gate == null) return super.mouseClicked(mouseX, mouseY, button);
        Element element = elementAt(mouseX, mouseY);
        if (button == 0) {
            Object[] source = sourceAt(mouseX, mouseY);
            if (source != null) {
                dragging = true;
                dragged = source[0];
                return true;
            }
            if (element != null) {
                if (element.param < 0) clickStatement(gate, element);
                else clickParameter(gate, element);
                return true;
            }
            for (Connector connector : connectors()) {
                if (connector.index < gate.connections.length && in(mouseX, mouseY, connector.x, connector.y, SLOT, SLOT)) {
                    clickSound();
                    boolean connected = !gate.connections[connector.index];
                    ClientPlatform.get().sendToServer(GateMenu.CHANNEL,
                            buffer -> GateMenu.writeConnectionChange(buffer, menu.containerId, connector.index, connected));
                    return true;
                }
            }
        } else if (button == 1 && element != null) {
            dragging = true;
            dragged = valueOf(gate, element);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Upstream {@code GuiElementStatement.onMouseClicked}: the ring holds the statement's choices the gate can use. */
    private void clickStatement(GateLogic gate, Element element) {
        if (element.index >= gate.statements.length) return;
        if (Screen.hasShiftDown()) {
            set(element, null);
            return;
        }
        StatementWrapper current = statement(gate, element.index, element.action).get();
        if (current == null) return;
        List<Object> possible = new ArrayList<>(Arrays.asList(current.getPossible()));
        if (!current.isPossibleOrdered()) {
            List<Object> list = new ArrayList<>();
            list.add(null);
            for (Object option : possible) {
                if (option != null) list.add(option);
            }
            possible = list;
        }
        possible.removeIf(option -> !isPossible(option, element.action));
        ring = new SlotRing(element.x, element.y, possible, value -> canSet(element, value), value -> set(element, value));
    }

    /** Upstream {@code GuiElementStatementParam.onMouseClicked}: the parameter takes the carried stack, or opens its ring. */
    private void clickParameter(GateLogic gate, Element element) {
        if (element.index >= gate.statements.length) return;
        FullStatement<? extends StatementWrapper> full = statement(gate, element.index, element.action);
        StatementParameter current = full.get(element.param);
        if (current == null || full.get() == null) return;
        StatementParameter clicked = current.onClick(gate, full.get(), menu.getCarried(), new StatementParameter.MouseClick(0, false));
        if (clicked != null) {
            set(element, clicked);
            return;
        }
        StatementParameter[] options = current.getPossible(gate);
        if (options == null) return;
        List<Object> possible = new ArrayList<>();
        for (StatementParameter option : options) {
            if (option != null || current.isPossibleOrdered()) possible.add(option);
        }
        ring = new SlotRing(element.x, element.y, possible, value -> canSet(element, value), value -> set(element, value));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ring != null) {
            SlotRing closing = ring;
            ring = null;
            closing.release(mouseX, mouseY);
            return true;
        }
        if (dragging) {
            Element target = elementAt(mouseX, mouseY);
            if (target != null) set(target, dragged);
            dragging = false;
            dragged = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
