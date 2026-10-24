package ports.gridwright.item;

import ports.gridwright.registry.ModUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * A crafting table you carry: using it opens the ordinary 3x3 crafting grid. The grid stays usable only
 * while the player still has a pocket workbench in their inventory; closing the screen returns whatever
 * is left in the grid, exactly as a placed crafting table does.
 */
public class PocketWorkbenchItem extends Item {

    public static final Component TITLE = Component.translatable("container.crafting");

    public PocketWorkbenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new Menu(id, inventory, opener), TITLE));
            player.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** The vanilla crafting menu, minus its "is the table block still there" check. */
    public static final class Menu extends CraftingMenu {
        public Menu(int id, Inventory inventory, Player player) {
            super(id, inventory, ContainerLevelAccess.create(player.level, player.blockPosition()));
        }

        @Override
        public boolean stillValid(Player player) {
            return player.getInventory().hasAnyOf(Set.of(ModUtilities.POCKET_WORKBENCH.get()));
        }
    }
}
