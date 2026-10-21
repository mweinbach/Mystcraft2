package ports.buildcraft.core.statements;

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

/**
 * Upstream {@code TriggerInventoryLevel}: whether the neighbouring inventory, or the room it has for one
 * item, is filled below a quarter, a half or three quarters.
 */
public class TriggerInventoryLevel extends BCStatement implements Trigger.External {
    public enum TriggerType {
        BELOW25(0.25F),
        BELOW50(0.5F),
        BELOW75(0.75F);

        public final float level;

        TriggerType(float level) {
            this.level = level;
        }
    }

    public final TriggerType type;
    private final ResourceLocation sprite;

    TriggerInventoryLevel(TriggerType type) {
        super("buildcraft:inventorylevel." + name(type), "buildcraft.inventorylevel." + name(type), "buildcraft.filteredBuffer." + name(type));
        this.type = type;
        this.sprite = sprite("trigger_inventory_" + name(type));
    }

    private static String name(TriggerType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.inventorylevel.below", (int) (type.level * 100));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, StatementContainer container, StatementParameter[] parameters) {
        ItemPort port = BCCoreStatements.items(tile, side);
        if (port == null) return false;
        ItemStack search = getParam(0, parameters, StatementParameterItemStack.EMPTY).getItemStack();
        int itemSpace = 0;
        int foundItems = 0;
        for (ItemPort.SlotView slot : port.slots()) {
            ItemStack inSlot = slot.stack();
            if (inSlot.isEmpty()) {
                if (search.isEmpty()) {
                    itemSpace += slot.limit();
                } else {
                    int count = Math.min(slot.limit(), search.getMaxStackSize());
                    ItemStack stack = search.copy();
                    stack.setCount(count);
                    itemSpace += count - slot.simulateInsert(stack).getCount();
                }
            } else if (search.isEmpty() || TriggerInventory.canMerge(search, inSlot)) {
                itemSpace += Math.min(inSlot.getMaxStackSize(), slot.limit());
                foundItems += inSlot.getCount();
            }
        }
        return itemSpace > 0 && foundItems / (float) itemSpace < type.level;
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
