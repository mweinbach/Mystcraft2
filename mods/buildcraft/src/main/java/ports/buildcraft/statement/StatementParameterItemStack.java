package ports.buildcraft.statement;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Upstream {@code StatementParameterItemStack}: a single item, set by clicking the slot with it. */
public class StatementParameterItemStack implements StatementParameter {
    public static final String TAG = "buildcraft:stack";
    public static final StatementParameterItemStack EMPTY = new StatementParameterItemStack(ItemStack.EMPTY);

    protected final ItemStack stack;

    public StatementParameterItemStack(ItemStack stack) {
        this.stack = stack;
    }

    public StatementParameterItemStack(CompoundTag tag) {
        this(ItemStack.of(tag.getCompound("stack")));
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        if (!stack.isEmpty()) tag.put("stack", stack.save(new CompoundTag()));
    }

    @Nullable
    @Override
    public ResourceLocation getSprite() {
        return null;
    }

    @Override
    public ItemStack getItemStack() {
        return stack;
    }

    @Override
    public StatementParameterItemStack onClick(StatementContainer source, Statement statement, ItemStack held, MouseClick mouse) {
        if (held.isEmpty()) return EMPTY;
        ItemStack copy = held.copy();
        copy.setCount(1);
        return new StatementParameterItemStack(copy);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StatementParameterItemStack param && ItemStack.matches(stack, param.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack.getItem(), stack.getCount(), stack.getTag());
    }

    @Nullable
    @Override
    public Component getDescription() {
        return stack.isEmpty() ? null : stack.getHoverName();
    }

    @Override
    public List<Component> getTooltip() {
        return stack.isEmpty() ? List.of() : stack.getTooltipLines(null, TooltipFlag.Default.NORMAL);
    }

    @Override
    public String getUniqueTag() {
        return TAG;
    }

    @Override
    public StatementParameter rotateLeft() {
        return this;
    }

    @Nullable
    @Override
    public StatementParameter[] getPossible(StatementContainer source) {
        return null;
    }
}
