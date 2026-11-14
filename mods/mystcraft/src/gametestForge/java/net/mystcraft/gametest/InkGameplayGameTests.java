package net.mystcraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.mystcraft.integration.MystcraftBootstrap;
import net.mystcraft.integration.registry.InkRegistry;

/** Runs the shared {@link InkGameplayTests} against Forge capabilities and {@code ForgeInkFluidType}. */
@GameTestHolder(MystcraftBootstrap.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InkGameplayGameTests {
    private static final String FLOOR = InkGameplayTests.FLOOR;
    private static final InkGameplayTests.Transfer TRANSFER = new ForgeTransfer();

    private InkGameplayGameTests() {}

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void craftAndReadGuide(GameTestHelper helper) {
        GuideGameplayTests.craftAndReadGuide(helper);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void compileGuideExample(GameTestHelper helper) {
        GuideAgeGameTests.compileGuideExample(helper);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void vialDrainsOneIndivisibleBucket(GameTestHelper helper) {
        InkGameplayTests.vialDrainsOneIndivisibleBucket(helper, TRANSFER);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void bucketExchangesWholeBuckets(GameTestHelper helper) {
        InkGameplayTests.bucketExchangesWholeBuckets(helper, TRANSFER);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void deskInkwellTransfersFractionalInk(GameTestHelper helper) {
        InkGameplayTests.deskInkwellTransfersFractionalInk(helper, TRANSFER);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void lecternInventoryHoldsOneBook(GameTestHelper helper) {
        InkGameplayTests.lecternInventoryHoldsOneBook(helper, TRANSFER);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void inkPathsAsWater(GameTestHelper helper) {
        InkGameplayTests.inkPathsAsWater(helper);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void entityInInkSwimsAndDrowns(GameTestHelper helper) {
        InkGameplayTests.entityInInkSwimsAndDrowns(helper, TRANSFER);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void inkPhysicsMatchesForgeFluidType(GameTestHelper helper) {
        InkGameplayTests.inkPhysicsMatchesForgeFluidType(helper, TRANSFER);
    }

    @GameTest(template = FLOOR, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void explorationFeaturesFollowTheBiomeTag(GameTestHelper helper) {
        InkGameplayTests.explorationFeaturesFollowTheBiomeTag(helper);
    }

    private static final class ForgeTransfer implements InkGameplayTests.Transfer {
        private static IFluidHandler.FluidAction action(boolean execute) {
            return execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        }

        private static IFluidHandlerItem item(ItemStack container) {
            return FluidUtil.getFluidHandler(container.copy()).resolve().orElse(null);
        }

        private static <T> T block(ServerLevel level, BlockPos pos, Direction side, net.minecraftforge.common.capabilities.Capability<T> capability) {
            BlockEntity entity = level.getBlockEntity(pos);
            return entity == null ? null : entity.getCapability(capability, side).resolve().orElse(null);
        }

        @Override public InkGameplayTests.Exchange drainItem(ItemStack container, int maximum, boolean execute) {
            IFluidHandlerItem handler = item(container);
            if (handler == null) return null;
            int drained = handler.drain(new FluidStack(InkRegistry.BLACK_INK.get(), maximum), action(execute)).getAmount();
            return new InkGameplayTests.Exchange(drained, handler.getContainer().copy());
        }

        @Override public InkGameplayTests.Exchange fillItem(ItemStack container, int amount, boolean execute) {
            IFluidHandlerItem handler = item(container);
            if (handler == null) return null;
            int filled = handler.fill(new FluidStack(InkRegistry.BLACK_INK.get(), amount), action(execute));
            return new InkGameplayTests.Exchange(filled, handler.getContainer().copy());
        }

        @Override public int fillTank(ServerLevel level, BlockPos pos, Direction side, Fluid fluid, int amount, boolean execute) {
            IFluidHandler handler = block(level, pos, side, ForgeCapabilities.FLUID_HANDLER);
            return handler == null ? -1 : handler.fill(new FluidStack(fluid, amount), action(execute));
        }

        @Override public int drainTank(ServerLevel level, BlockPos pos, Direction side, int amount, boolean execute) {
            IFluidHandler handler = block(level, pos, side, ForgeCapabilities.FLUID_HANDLER);
            return handler == null ? -1 : handler.drain(new FluidStack(InkRegistry.BLACK_INK.get(), amount), action(execute)).getAmount();
        }

        @Override public ItemStack insertItem(ServerLevel level, BlockPos pos, Direction side, ItemStack stack, boolean execute) {
            IItemHandler handler = block(level, pos, side, ForgeCapabilities.ITEM_HANDLER);
            if (handler == null) return null;
            ItemStack remainder = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) remainder = handler.insertItem(slot, remainder, !execute);
            return remainder;
        }

        @Override public ItemStack extractItem(ServerLevel level, BlockPos pos, Direction side, int amount, boolean execute) {
            IItemHandler handler = block(level, pos, side, ForgeCapabilities.ITEM_HANDLER);
            if (handler == null) return null;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack extracted = handler.extractItem(slot, amount, !execute);
                if (!extracted.isEmpty()) return extracted;
            }
            return ItemStack.EMPTY;
        }

        @Override public boolean isEyeInInk(Entity entity) {
            return entity.isEyeInFluidType(InkRegistry.BLACK_INK.get().getFluidType());
        }

        @Override public InkGameplayTests.InkPhysics inkPhysics(ServerLevel level) {
            FluidType type = InkRegistry.BLACK_INK.get().getFluidType();
            LivingEntity cow = EntityType.COW.create(level);
            Boat boat = EntityType.BOAT.create(level);
            BlockPos pos = BlockPos.ZERO;
            var state = InkRegistry.BLACK_INK.get().defaultFluidState();
            try {
                return new InkGameplayTests.InkPhysics(type.canSwim(cow), type.canDrownIn(cow), type.supportsBoating(boat),
                        type.canPushEntity(cow), type.motionScale(cow), type.getFallDistanceModifier(cow), type.canExtinguish(cow),
                        type.getBlockPathType(state, level, pos, null, false),
                        type.getAdjacentBlockPathType(state, level, pos, null, net.minecraft.world.level.pathfinder.BlockPathTypes.WALKABLE));
            } finally {
                cow.discard();
                boat.discard();
            }
        }
    }
}
