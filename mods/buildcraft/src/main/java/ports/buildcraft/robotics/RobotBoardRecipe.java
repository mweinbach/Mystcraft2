package ports.buildcraft.robotics;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import ports.buildcraft.robotics.board.RobotBoardType;

/**
 * Programming a robot in the crafting grid, written for this port: a robot and a programmed redstone board make the
 * robot with that board, and the board it had comes back in the robot's slot. A robot with a board on its own gives
 * the robot back without it, and the board in its slot. Energy stays with the robot.
 */
public final class RobotBoardRecipe implements CraftingRecipe {
    private final ResourceLocation id;

    public RobotBoardRecipe(ResourceLocation id) {
        this.id = id;
    }

    private record Grid(int robotSlot, ItemStack robot, ItemStack board) {}

    private static Grid read(CraftingContainer inventory) {
        int robotSlot = -1;
        ItemStack robot = ItemStack.EMPTY, board = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof RobotItem && robot.isEmpty()) {
                robot = stack;
                robotSlot = slot;
            } else if (stack.getItem() instanceof RedstoneBoardItem item && item.type != null && board.isEmpty()) {
                board = stack;
            } else {
                return null;
            }
        }
        if (robot.isEmpty()) return null;
        if (board.isEmpty() && RobotItem.board(robot) == null) return null;
        return new Grid(robotSlot, robot, board);
    }

    @Override
    public boolean matches(CraftingContainer inventory, Level level) {
        return read(inventory) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory) {
        Grid grid = read(inventory);
        if (grid == null) return ItemStack.EMPTY;
        RobotBoardType board = grid.board.isEmpty() ? null : ((RedstoneBoardItem) grid.board.getItem()).type;
        return RobotItem.withBoard(grid.robot, board);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inventory) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        Grid grid = read(inventory);
        RobotBoardType old = grid == null ? null : RobotItem.board(grid.robot);
        if (old != null) remaining.set(grid.robotSlot, new ItemStack(BCRobotics.BOARDS.get(old).get()));
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BCRobotics.ROBOT_BOARD_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    public static final class Serializer implements RecipeSerializer<RobotBoardRecipe> {
        @Override
        public RobotBoardRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new RobotBoardRecipe(id);
        }

        @Override
        public RobotBoardRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new RobotBoardRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RobotBoardRecipe recipe) {}
    }
}
