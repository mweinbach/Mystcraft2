package ports.buildcraft.builders;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.BlueprintBuilder;
import ports.buildcraft.builders.snapshot.InvalidSnapshotException;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;

/**
 * Upstream {@code ItemSchematicSingle}: a blank one used on a block records that block's schematic, and a
 * recorded one builds it again from the items in the player's inventory. Used while sneaking, it is wiped.
 * Upstream's metadata 0 and 1 are two items, {@code schematic_single_clean} and {@code schematic_single_used}.
 * Upstream commented out its recipe, so it is creative-only.
 */
public class SchematicSingleItem extends Item {
    public static final String NBT_KEY = "schematic";
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    public final boolean used;

    public SchematicSingleItem(boolean used, Properties properties) {
        super(properties.stacksTo(used ? 1 : 16));
        this.used = used;
    }

    public static ItemStack getUsed(SchematicBlock schematic) {
        ItemStack stack = new ItemStack(BCBuilders.SCHEMATIC_SINGLE_USED.get());
        stack.getOrCreateTag().put(NBT_KEY, SchematicBlockManager.writeToNBT(schematic));
        return stack;
    }

    /** Upstream {@code getSchematic}: the recorded schematic, or null for anything but a single schematic. */
    @Nullable
    public static SchematicBlock getSchematic(ItemStack stack) throws InvalidSnapshotException {
        if (!(stack.getItem() instanceof SchematicSingleItem)) return null;
        CompoundTag tag = stack.getTag();
        return SchematicBlockManager.readFromNBT(tag != null ? tag.getCompound(NBT_KEY) : new CompoundTag());
    }

    /** Upstream {@code onItemRightClick}: used in the air while sneaking, it is wiped. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        return InteractionResultHolder.success(clear(stack));
    }

    /**
     * Upstream {@code onItemUseFirst}, which ran before the clicked block's own use; each loader calls it from
     * its block click event. Clients leave the click to the server, so that doors and chests do not react.
     */
    public static InteractionResult useFirst(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof SchematicSingleItem item) || player.isSpectator()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            player.setItemInHand(hand, clear(stack));
            return InteractionResult.SUCCESS;
        }
        return item.used ? place(player, level, hand, stack, hit.getBlockPos(), hit.getDirection())
                : record(player, level, hand, stack, hit.getBlockPos());
    }

    private static ItemStack clear(ItemStack stack) {
        ItemStack clean = new ItemStack(BCBuilders.SCHEMATIC_SINGLE_CLEAN.get(), stack.getCount());
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            CompoundTag rest = tag.copy();
            rest.remove(NBT_KEY);
            if (!rest.isEmpty()) clean.setTag(rest);
        }
        return clean;
    }

    /** Upstream recorded the whole held stack into one unstackable item; here one blank of the stack is recorded. */
    private static InteractionResult record(Player player, Level level, InteractionHand hand, ItemStack stack, BlockPos pos) {
        SchematicBlock schematic = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(level, pos, pos));
        if (schematic.isAir()) return InteractionResult.FAIL;
        ItemStack recorded = getUsed(schematic);
        if (stack.getCount() == 1) {
            player.setItemInHand(hand, recorded);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(recorded)) player.drop(recorded, false);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult place(Player player, Level level, InteractionHand hand, ItemStack stack, BlockPos pos, Direction side) {
        boolean replaceable = level.getBlockState(pos).getMaterial().isReplaceable();
        BlockPos placePos = replaceable ? pos : pos.relative(side);
        if (!level.getBlockState(placePos).getMaterial().isReplaceable() || !level.mayInteract(player, placePos)
                || !player.mayUseItemAt(placePos, side, stack)) {
            return InteractionResult.FAIL;
        }
        if (replaceable && !level.isEmptyBlock(placePos)) level.removeBlock(placePos, false);
        SchematicBlock schematic;
        try {
            schematic = getSchematic(stack);
        } catch (InvalidSnapshotException e) {
            player.displayClientMessage(Component.literal("Invalid schematic: " + e.getMessage()), true);
            LOGGER.warn("Invalid single schematic", e);
            return InteractionResult.FAIL;
        }
        if (schematic == null || schematic.isBuilt(level, placePos) || !schematic.canBuild(level, placePos)) return InteractionResult.FAIL;
        if (!schematic.computeRequiredFluids().isEmpty()) {
            player.displayClientMessage(Component.literal("Schematic requires fluids"), true);
            return InteractionResult.FAIL;
        }
        List<ItemStack> required = BlueprintBuilder.mergeSameItems(schematic.computeRequiredItems());
        Inventory inventory = player.getInventory();
        if (!required.stream().allMatch(needed -> count(inventory, needed) >= needed.getCount())) {
            MutableComponent message = Component.literal("Not enough items. Total needed: ");
            for (int i = 0; i < required.size(); i++) {
                if (i > 0) message.append(", ");
                message.append(required.get(i).getDisplayName()).append(" x " + required.get(i).getCount());
            }
            player.displayClientMessage(message, true);
            return InteractionResult.FAIL;
        }
        if (!schematic.build(level, placePos)) return InteractionResult.FAIL;
        required.forEach(needed -> extract(inventory, needed));
        SoundType sound = level.getBlockState(placePos).getSoundType();
        level.playSound(null, placePos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }

    private static int count(Inventory inventory, ItemStack needed) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameTags(stack, needed)) count += stack.getCount();
        }
        return count;
    }

    private static void extract(Inventory inventory, ItemStack needed) {
        int left = needed.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameTags(stack, needed)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        inventory.setChanged();
    }

    /** Upstream drew a used schematic's block under its tooltip as a one-block blueprint. */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (!used || tag == null || !tag.contains(NBT_KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        try {
            SchematicBlock schematic = getSchematic(stack);
            if (schematic == null) return Optional.empty();
            Blueprint blueprint = new Blueprint();
            blueprint.size = new BlockPos(1, 1, 1);
            blueprint.data = new int[] {0};
            blueprint.palette.add(schematic);
            blueprint.computeKey();
            return Optional.of(SnapshotTooltip.of(blueprint));
        } catch (InvalidSnapshotException e) {
            return Optional.empty();
        }
    }
}
