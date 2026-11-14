package net.mystcraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.mystcraft.integration.registry.InkRegistry;
import ports.fluidphysics.FluidPhysics;
import ports.fluidphysics.FluidPhysicsSupport;
import ports.fluidphysics.FluidProperties;

/** Runs the shared {@link InkGameplayTests} against the Fabric Transfer API and the bundled Fluid Physics library. */
public final class InkGameplayGameTests implements FabricGameTest {
    private static final String FLOOR = "mystcraft:" + InkGameplayTests.FLOOR;
    private static final InkGameplayTests.Transfer TRANSFER = new FabricTransfer();

    public InkGameplayGameTests() {}

    @GameTest(template = FLOOR)
    public void craftAndReadGuide(GameTestHelper helper) {
        GuideGameplayTests.craftAndReadGuide(helper);
    }

    @GameTest(template = FLOOR)
    public void compileGuideExample(GameTestHelper helper) {
        GuideAgeGameTests.compileGuideExample(helper);
    }

    @GameTest(template = FLOOR)
    public void vialDrainsOneIndivisibleBucket(GameTestHelper helper) {
        InkGameplayTests.vialDrainsOneIndivisibleBucket(helper, TRANSFER);
    }

    @GameTest(template = FLOOR)
    public void bucketExchangesWholeBuckets(GameTestHelper helper) {
        InkGameplayTests.bucketExchangesWholeBuckets(helper, TRANSFER);
    }

    @GameTest(template = FLOOR)
    public void deskInkwellTransfersFractionalInk(GameTestHelper helper) {
        InkGameplayTests.deskInkwellTransfersFractionalInk(helper, TRANSFER);
    }

    @GameTest(template = FLOOR)
    public void lecternInventoryHoldsOneBook(GameTestHelper helper) {
        InkGameplayTests.lecternInventoryHoldsOneBook(helper, TRANSFER);
    }

    @GameTest(template = FLOOR)
    public void inkPathsAsWater(GameTestHelper helper) {
        InkGameplayTests.inkPathsAsWater(helper);
    }

    @GameTest(template = FLOOR)
    public void entityInInkSwimsAndDrowns(GameTestHelper helper) {
        InkGameplayTests.entityInInkSwimsAndDrowns(helper, TRANSFER);
    }

    @GameTest(template = FLOOR)
    public void inkPhysicsMatchesForgeFluidType(GameTestHelper helper) {
        InkGameplayTests.inkPhysicsMatchesForgeFluidType(helper, TRANSFER);
    }

    @GameTest(template = FLOOR)
    public void explorationFeaturesFollowTheBiomeTag(GameTestHelper helper) {
        InkGameplayTests.explorationFeaturesFollowTheBiomeTag(helper);
    }

    /** One millibucket is 81 droplets; every amount here is a whole number of millibuckets. */
    private static final class FabricTransfer implements InkGameplayTests.Transfer {
        private static final long UNIT = FluidConstants.BUCKET / 1000;

        private static FluidVariant ink() {
            return FluidVariant.of(InkRegistry.BLACK_INK.get());
        }

        private static InkGameplayTests.Exchange exchange(ItemStack container, int amount, boolean execute, boolean fill) {
            SimpleContainer inventory = new SimpleContainer(1);
            inventory.setItem(0, container.copy());
            ContainerItemContext context = ContainerItemContext.ofSingleSlot(InventoryStorage.of(inventory, null).getSlot(0));
            Storage<FluidVariant> storage = FluidStorage.ITEM.find(container, context);
            if (storage == null) return null;
            long moved;
            try (Transaction transaction = Transaction.openOuter()) {
                moved = fill ? storage.insert(ink(), amount * UNIT, transaction) : storage.extract(ink(), amount * UNIT, transaction);
                if (execute) transaction.commit();
            }
            return new InkGameplayTests.Exchange((int) (moved / UNIT), inventory.getItem(0).copy());
        }

        @Override public InkGameplayTests.Exchange drainItem(ItemStack container, int maximum, boolean execute) {
            return exchange(container, maximum, execute, false);
        }

        @Override public InkGameplayTests.Exchange fillItem(ItemStack container, int amount, boolean execute) {
            return exchange(container, amount, execute, true);
        }

        @Override public int fillTank(ServerLevel level, BlockPos pos, Direction side, Fluid fluid, int amount, boolean execute) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
            if (storage == null) return -1;
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = storage.insert(FluidVariant.of(fluid), amount * UNIT, transaction);
                if (execute) transaction.commit();
                return (int) (moved / UNIT);
            }
        }

        @Override public int drainTank(ServerLevel level, BlockPos pos, Direction side, int amount, boolean execute) {
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
            if (storage == null) return -1;
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = storage.extract(ink(), amount * UNIT, transaction);
                if (execute) transaction.commit();
                return (int) (moved / UNIT);
            }
        }

        @Override public ItemStack insertItem(ServerLevel level, BlockPos pos, Direction side, ItemStack stack, boolean execute) {
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
            if (storage == null) return null;
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                if (execute) transaction.commit();
                ItemStack remainder = stack.copy();
                remainder.shrink((int) inserted);
                return remainder;
            }
        }

        @Override public ItemStack extractItem(ServerLevel level, BlockPos pos, Direction side, int amount, boolean execute) {
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
            if (storage == null) return null;
            try (Transaction transaction = Transaction.openOuter()) {
                ItemVariant resource = null;
                for (StorageView<ItemVariant> view : storage) {
                    if (!view.isResourceBlank()) { resource = view.getResource(); break; }
                }
                if (resource == null) return ItemStack.EMPTY;
                long extracted = storage.extract(resource, amount, transaction);
                if (execute) transaction.commit();
                return resource.toStack((int) extracted);
            }
        }

        @Override public boolean isEyeInInk(Entity entity) {
            FluidProperties ink = FluidPhysics.propertiesOf(InkRegistry.BLACK_INK.get());
            return ink != null && FluidPhysicsSupport.isEyeInFluid(entity, properties -> properties == ink);
        }

        @Override public InkGameplayTests.InkPhysics inkPhysics(ServerLevel level) {
            FluidProperties still = FluidPhysics.propertiesOf(InkRegistry.BLACK_INK.get());
            FluidProperties flowing = FluidPhysics.propertiesOf(InkRegistry.FLOWING_BLACK_INK.get());
            if (still == null || still != flowing) throw new IllegalStateException("black ink still/flowing are not registered together with Fluid Physics");
            return new InkGameplayTests.InkPhysics(still.canSwim(), still.canDrown(), still.supportsBoating(), still.canPushEntity(),
                    still.motionScale(), still.fallDistanceModifier(), still.canExtinguish(), still.pathType(), still.adjacentPathType());
        }
    }
}
