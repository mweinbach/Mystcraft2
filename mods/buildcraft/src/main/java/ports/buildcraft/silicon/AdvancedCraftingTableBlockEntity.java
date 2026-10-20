package ports.buildcraft.silicon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SlotRange;
import ports.buildcraft.lib.WorkbenchCrafting;

/**
 * Upstream {@code TileAdvancedCraftingTable}: crafts its phantom 3x3 blueprint from the fifteen material slots
 * into the nine result slots, spending 500 MJ of laser power per craft.
 */
public class AdvancedCraftingTableBlockEntity extends LaserTableBlockEntity {
    public static final long POWER_REQ = 500 * Power.MJ;
    public static final int BLUEPRINT_START = 0;
    public static final int BLUEPRINT_SLOTS = 9;
    public static final int MATERIALS_START = BLUEPRINT_START + BLUEPRINT_SLOTS;
    public static final int MATERIALS_SLOTS = 15;
    public static final int RESULTS_START = MATERIALS_START + MATERIALS_SLOTS;
    public static final int RESULTS_SLOTS = 9;
    public static final int SLOTS = RESULTS_START + RESULTS_SLOTS;

    private final SlotRange blueprint = new SlotRange(this, BLUEPRINT_START, BLUEPRINT_SLOTS);
    private final SlotRange materials = new SlotRange(this, MATERIALS_START, MATERIALS_SLOTS);
    private final SlotRange results = new SlotRange(this, RESULTS_START, RESULTS_SLOTS);
    private final WorkbenchCrafting crafting = new WorkbenchCrafting(3, 3, blueprint, materials, results);

    public AdvancedCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(BCSilicon.ADVANCED_CRAFTING_TABLE_TYPE.get(), pos, state, SLOTS);
    }

    @Override
    protected boolean isPhantom(int slot) {
        return slot < MATERIALS_START;
    }

    @Override
    protected boolean canInsert(int slot) {
        return materials.contains(slot);
    }

    @Override
    protected boolean canExtract(int slot) {
        return results.contains(slot);
    }

    @Override
    protected void onSlotChanged(int slot) {
        if (blueprint.contains(slot)) crafting.onBlueprintChanged();
        else if (materials.contains(slot)) crafting.onMaterialsChanged();
    }

    /** The result the blueprint would craft. */
    public ItemStack assumedResult() {
        return crafting.getAssumedResult();
    }

    @Override
    public long getTarget() {
        return crafting.canCraft() ? POWER_REQ : 0;
    }

    @Override
    void serverTick() {
        super.serverTick();
        crafting.tick(level);
        if (crafting.canCraft() && power >= POWER_REQ && crafting.craft(level, worldPosition)) power -= POWER_REQ;
        setChanged();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AdvancedCraftingTableMenu(id, inventory, this);
    }
}
