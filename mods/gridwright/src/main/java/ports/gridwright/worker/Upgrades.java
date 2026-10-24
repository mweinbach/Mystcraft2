package ports.gridwright.worker;

import ports.gridwright.registry.ModItems;
import ports.gridwright.transfer.TransferRegistry;
import ports.gridwright.transfer.UpgradeKind;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The upgrades in a quarry's upgrade slots. Speed (the transfer nodes' {@link UpgradeKind#SPEED}
 * item) counts up to {@link #MAX_SPEED} and fortune up to {@link #MAX_FORTUNE}, however they are split
 * across slots; silk touch replaces fortune when both are present.
 */
public record Upgrades(int speed, int fortune, boolean silkTouch) {

    public static final Upgrades NONE = new Upgrades(0, 0, false);
    public static final int MAX_SPEED = 8;
    public static final int MAX_FORTUNE = 3;

    public static ItemStack speed(int count) {
        return new ItemStack(TransferRegistry.UPGRADES.get(UpgradeKind.SPEED).get(), count);
    }

    private static boolean isSpeed(ItemStack stack) {
        return stack.is(TransferRegistry.UPGRADES.get(UpgradeKind.SPEED).get());
    }

    public static boolean isUpgrade(ItemStack stack) {
        return isSpeed(stack) || stack.is(ModItems.FORTUNE_UPGRADE.get())
                || stack.is(ModItems.SILK_TOUCH_UPGRADE.get());
    }

    /** Reads the upgrade slots of {@code container}. */
    public static Upgrades read(Container container, WorkerLayout layout) {
        int speed = 0;
        int fortune = 0;
        boolean silk = false;
        for (int slot = 0; slot < layout.size(); slot++) {
            if (layout.slot(slot).role() != WorkerLayout.Role.UPGRADE) continue;
            ItemStack stack = container.getItem(slot);
            if (isSpeed(stack)) speed += stack.getCount();
            else if (stack.is(ModItems.FORTUNE_UPGRADE.get())) fortune += stack.getCount();
            else if (stack.is(ModItems.SILK_TOUCH_UPGRADE.get())) silk = true;
        }
        return new Upgrades(Math.min(speed, MAX_SPEED), silk ? 0 : Math.min(fortune, MAX_FORTUNE), silk);
    }

    /**
     * The pickaxe a quarry mines with: netherite, so it can harvest every vanilla block, enchanted
     * with this fortune level or silk touch. Loot tables see it as the tool used.
     */
    public ItemStack tool() {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (silkTouch) tool.enchant(Enchantments.SILK_TOUCH, 1);
        else if (fortune > 0) tool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        return tool;
    }
}
