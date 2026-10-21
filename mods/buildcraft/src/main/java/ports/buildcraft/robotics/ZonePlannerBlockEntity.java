package ports.buildcraft.robotics;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.MapLocationItem;
import ports.buildcraft.core.PaintbrushItem;
import ports.buildcraft.lib.DeltaInt;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.robotics.zone.ZonePlan;

/**
 * Upstream {@code TileZonePlanner}. Sixteen stored brushes, an import pair and an export pair. A coloured brush
 * and a single zone map take 200 ticks to copy the zone onto that colour's layer, or the layer out onto a blank
 * map. Nothing is open to automation.
 */
public class ZonePlannerBlockEntity extends SyncedBlockEntity implements MenuProvider {
    public static final int LAYERS = 16;
    public static final int STORAGE = 16;
    public static final int SLOT_INPUT_BRUSH = 16;
    public static final int SLOT_INPUT_MAP = 17;
    public static final int SLOT_INPUT_RESULT = 18;
    public static final int SLOT_OUTPUT_BRUSH = 19;
    public static final int SLOT_OUTPUT_MAP = 20;
    public static final int SLOT_OUTPUT_RESULT = 21;
    public static final int SLOTS = 22;
    private static final int WORK = 200;

    public final ZonePlan[] layers = new ZonePlan[LAYERS];
    public final DeltaInt deltaProgressInput = new DeltaInt();
    public final DeltaInt deltaProgressOutput = new DeltaInt();
    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ZonePlannerBlockEntity.this.setChanged();
        }
    };
    private int progressInput;
    private int progressOutput;

    public ZonePlannerBlockEntity(BlockPos pos, BlockState state) {
        super(BCRobotics.ZONE_PLANNER_TYPE.get(), pos, state);
        for (int i = 0; i < layers.length; i++) layers[i] = new ZonePlan();
    }

    public SimpleContainer inventory() {
        return items;
    }

    public ItemStack getItem(int slot) {
        return items.getItem(slot);
    }

    public void setItem(int slot, ItemStack stack) {
        items.setItem(slot, stack);
    }

    public void serverTick() {
        long now = level.getGameTime();
        deltaProgressInput.tick(now);
        deltaProgressOutput.tick(now);
        if (importReady()) {
            if (progressInput == 0) {
                deltaProgressInput.addDelta(now, 0, WORK, 1);
                deltaProgressInput.addDelta(now, WORK, WORK + 5, -1);
                queueSync();
            }
            if (progressInput < WORK) {
                progressInput++;
                return;
            }
            DyeColor colour = PaintbrushItem.colorOf(items.getItem(SLOT_INPUT_BRUSH));
            ZonePlan plan = new ZonePlan();
            plan.read(items.getItem(SLOT_INPUT_MAP).getOrCreateTag());
            layers[colour.getId()] = plan.offset(-worldPosition.getX(), -worldPosition.getZ());
            items.setItem(SLOT_INPUT_MAP, ItemStack.EMPTY);
            items.setItem(SLOT_INPUT_RESULT, new ItemStack(BCCore.MAP_LOCATION.get()));
            progressInput = 0;
            setChanged();
            queueSync();
        } else if (progressInput != -1) {
            progressInput = -1;
            deltaProgressInput.setValue(0);
            queueSync();
        }
        if (exportReady()) {
            if (progressOutput == 0) {
                deltaProgressOutput.addDelta(now, 0, WORK, 1);
                deltaProgressOutput.addDelta(now, WORK, WORK + 5, -1);
                queueSync();
            }
            if (progressOutput < WORK) {
                progressOutput++;
                return;
            }
            DyeColor colour = PaintbrushItem.colorOf(items.getItem(SLOT_OUTPUT_BRUSH));
            ItemStack map = items.getItem(SLOT_OUTPUT_MAP);
            MapLocationItem.setZone(map, layers[colour.getId()].offset(worldPosition.getX(), worldPosition.getZ()));
            items.setItem(SLOT_OUTPUT_RESULT, map);
            items.setItem(SLOT_OUTPUT_MAP, ItemStack.EMPTY);
            progressOutput = 0;
            setChanged();
            queueSync();
        } else if (progressOutput != -1) {
            progressOutput = -1;
            deltaProgressOutput.setValue(0);
            queueSync();
        }
        flushSync();
    }

    /** A coloured brush and one map that already holds a zone, with the result slot empty. A clean brush is not ready. */
    private boolean importReady() {
        ItemStack brush = items.getItem(SLOT_INPUT_BRUSH);
        ItemStack map = items.getItem(SLOT_INPUT_MAP);
        return PaintbrushItem.colorOf(brush) != null && map.getItem() instanceof MapLocationItem && map.getCount() == 1
                && map.getTag() != null && map.getTag().contains("chunkMapping") && items.getItem(SLOT_INPUT_RESULT).isEmpty();
    }

    /** A coloured brush and one map location, with the result slot empty. */
    private boolean exportReady() {
        ItemStack brush = items.getItem(SLOT_OUTPUT_BRUSH);
        ItemStack map = items.getItem(SLOT_OUTPUT_MAP);
        return PaintbrushItem.colorOf(brush) != null && map.getItem() instanceof MapLocationItem && map.getCount() == 1
                && items.getItem(SLOT_OUTPUT_RESULT).isEmpty();
    }

    void applyPlan(int index, ZonePlan plan) {
        layers[index] = plan;
        setChanged();
        queueSync();
        flushSync();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.zone_planner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ZonePlannerMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.createTag());
        for (int i = 0; i < layers.length; i++) {
            CompoundTag layer = new CompoundTag();
            layers[i].write(layer);
            tag.put("layer_" + i, layer);
        }
        tag.putInt("progressInput", progressInput);
        tag.putInt("progressOutput", progressOutput);
        tag.put("deltaInput", deltaProgressInput.save());
        tag.put("deltaOutput", deltaProgressOutput.save());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        for (int slot = 0; slot < items.getContainerSize(); slot++) items.setItem(slot, ItemStack.EMPTY);
        items.fromTag(tag.getList("Items", Tag.TAG_COMPOUND));
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new ZonePlan();
            layers[i].read(tag.getCompound("layer_" + i));
        }
        progressInput = tag.getInt("progressInput");
        progressOutput = tag.getInt("progressOutput");
        deltaProgressInput.load(tag.getCompound("deltaInput"));
        deltaProgressOutput.load(tag.getCompound("deltaOutput"));
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        for (int i = 0; i < layers.length; i++) {
            CompoundTag layer = new CompoundTag();
            layers[i].write(layer);
            tag.put("layer_" + i, layer);
        }
        tag.put("deltaInput", deltaProgressInput.save());
        tag.put("deltaOutput", deltaProgressOutput.save());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new ZonePlan();
            layers[i].read(tag.getCompound("layer_" + i));
        }
        deltaProgressInput.load(tag.getCompound("deltaInput"));
        deltaProgressOutput.load(tag.getCompound("deltaOutput"));
    }
}
