package ports.buildcraft.silicon.plug;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableItem;
import ports.buildcraft.transport.pluggable.SimplePluggableItem;

/**
 * Upstream {@code ItemPluggableFacade}. One item, with the copied state and hollow flag in {@code facade} NBT.
 * The creative tab lists a phased example and every visible state, full and hollow.
 */
public class FacadeItem extends Item implements PluggableItem {
    public FacadeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(FacadeInstance states) {
        ItemStack stack = new ItemStack(BCSilicon.FACADE_ITEM.get());
        stack.getOrCreateTag().put("facade", states.write());
        return stack;
    }

    public static ItemStack create(FacadeBlockStateInfo info, boolean hollow) {
        ItemStack stack = create(FacadeInstance.single(info, hollow));
        stack.setCount(FacadeAssemblyRecipe.OUTPUT_COUNT);
        return stack;
    }

    public static FacadeInstance get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return FacadeInstance.single(FacadeStateManager.defaultState(), false);
        if ("basic".equalsIgnoreCase(tag.getString("preview"))) {
            FacadeBlockStateInfo preview = FacadeStateManager.info(Blocks.BRICKS.defaultBlockState());
            return FacadeInstance.single(preview, false);
        }
        if (!tag.contains("facade") && tag.contains("states", Tag.TAG_LIST)) {
            ListTag states = tag.getList("states", Tag.TAG_COMPOUND);
            if (!states.isEmpty()) {
                CompoundTag facade = new CompoundTag();
                facade.putBoolean("isHollow", states.getCompound(0).getBoolean("isHollow"));
                facade.put("states", states);
                tag.put("facade", facade);
            }
        }
        return FacadeInstance.read(tag.getCompound("facade"));
    }

    @Nullable
    @Override
    public PipePluggable onPlace(ItemStack stack, PipeBlockEntity holder, Direction side, Player player, InteractionHand hand) {
        FacadeInstance states = get(stack);
        BlockState state = states.phasedStates[0].info.state;
        SimplePluggableItem.playPlaceSound(holder.getLevel(), holder.getBlockPos(), state.getSoundType());
        return new PluggableFacade(BCSilicon.FACADE, holder, side, states);
    }

    @Override
    public Component getName(ItemStack stack) {
        FacadeInstance states = get(stack);
        Component name = Component.translatable(getDescriptionId());
        if (states.phased) return Component.translatable("item.buildcraft.plug_facade.phased");
        return name.copy().append(": ").append(blockName(states.phasedStates[0]));
    }

    public static Component blockName(FacadePhasedState state) {
        ItemStack required = state.info.requiredStack;
        if (!required.isEmpty()) return required.getHoverName();
        return state.info.state.getBlock().getName();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        FacadeInstance states = get(stack);
        if (states.phased) {
            FacadePhasedState fallback = null;
            for (FacadePhasedState state : states.phasedStates) {
                if (state.activeColour == null) {
                    fallback = state;
                    continue;
                }
                tooltip.add(Component.translatable("item.buildcraft.plug_facade.phased.state",
                        Component.translatable("color.minecraft." + state.activeColour.getName()), blockName(state)));
            }
            if (fallback != null) {
                tooltip.add(0, Component.translatable("item.buildcraft.plug_facade.phased.default", blockName(fallback)));
            }
            return;
        }
        if (states.hollow) {
            tooltip.add(Component.translatable("item.buildcraft.plug_facade.hollow").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal(String.valueOf(Registry.BLOCK.getKey(states.phasedStates[0].info.state.getBlock()))));
        }
        FacadeBlockStateInfo info = states.phasedStates[0].info;
        BlockState state = info.state;
        for (Property<?> property : info.varyingProperties) {
            tooltip.add(Component.literal(property.getName() + " = " + propertyName(property, state))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static <T extends Comparable<T>> String propertyName(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    @Override
    public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        if (!allowedIn(tab)) return;
        FacadeStateManager.refresh();
        FacadeBlockStateInfo stone = FacadeStateManager.info(Blocks.STONE.defaultBlockState());
        FacadeBlockStateInfo planks = FacadeStateManager.info(Blocks.OAK_PLANKS.defaultBlockState());
        FacadeBlockStateInfo log = FacadeStateManager.info(Blocks.OAK_LOG.defaultBlockState());
        if (stone.visible && planks.visible && log.visible) {
            items.add(create(new FacadeInstance(new FacadePhasedState[] {
                    new FacadePhasedState(stone, null),
                    new FacadePhasedState(planks, DyeColor.RED),
                    new FacadePhasedState(log, DyeColor.CYAN)
            }, false)));
        }
        for (FacadeBlockStateInfo info : FacadeStateManager.visible()) {
            ItemStack full = create(info, false);
            full.setCount(1);
            ItemStack hollow = create(info, true);
            hollow.setCount(1);
            items.add(full);
            items.add(hollow);
        }
    }
}
