package ports.buildcraft.core.statements;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementParameterItemStack;
import ports.buildcraft.statement.Trigger;

/** Upstream {@code TriggerInventory}: whether the neighbouring inventory is empty, holds an item, has room for one, or is full. */
public class TriggerInventory extends BCStatement implements Trigger.External {
    public enum State {
        EMPTY,
        CONTAINS,
        SPACE,
        FULL
    }

    public final State state;
    private final ResourceLocation sprite;

    TriggerInventory(State state) {
        super("buildcraft:inventory." + name(state), "buildcraft.inventory." + name(state));
        this.state = state;
        this.sprite = sprite("trigger_inventory_" + name(state));
    }

    private static String name(State state) {
        return state.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public int maxParameters() {
        return state == State.CONTAINS || state == State.SPACE ? 1 : 0;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.inventory." + name(state));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, StatementContainer container, StatementParameter[] parameters) {
        ItemStack searched = parameters.length >= 1 && parameters[0] != null ? parameters[0].getItemStack() : ItemStack.EMPTY;
        ItemPort port = BCCoreStatements.items(tile, side);
        if (port == null) return false;
        List<ItemPort.SlotView> slots = port.slots();
        if (slots.isEmpty()) return false;
        boolean foundItems = false;
        boolean foundSpace = false;
        for (ItemPort.SlotView slot : slots) {
            ItemStack stack = slot.stack();
            foundItems |= !stack.isEmpty() && (searched.isEmpty() || canMerge(stack, searched));
            foundSpace |= (stack.isEmpty() || (canMerge(stack, searched) && stack.getCount() < stack.getMaxStackSize()))
                    && (searched.isEmpty() || slot.simulateInsert(searched).isEmpty());
        }
        return switch (state) {
            case EMPTY -> !foundItems;
            case CONTAINS -> foundItems;
            case SPACE -> foundSpace;
            case FULL -> !foundSpace;
        };
    }

    /** Upstream {@code StackUtil.canStacksOrListsMerge}, without lists: false when either stack is empty. */
    static boolean canMerge(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a, b);
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return StatementParameterItemStack.EMPTY;
    }

    @Override
    public Statement[] getPossible() {
        return BCCoreStatements.TRIGGER_INVENTORY_ALL;
    }
}
