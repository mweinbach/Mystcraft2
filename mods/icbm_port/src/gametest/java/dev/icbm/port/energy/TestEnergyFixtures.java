package dev.icbm.port.energy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyItem;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Test-only stand-ins for another mod's Team Reborn Energy devices. They live in the GameTest mod,
 * never in the production JAR. Transfer limits are below the stored amount so EMP has to make
 * several extraction calls, as it would against a rate-limited machine.
 */
public final class TestEnergyFixtures implements ModInitializer {
  public static final String NAMESPACE = "icbm_port_gametest";
  public static final long CAPACITY = 100_000, TRANSFER = 10_000;

  public static final Block BATTERY_BLOCK = new BatteryBlock();
  public static final Item BATTERY_ITEM = new BatteryItem();
  public static final BlockEntityType<BatteryBlockEntity> BATTERY_BLOCK_ENTITY =
      FabricBlockEntityTypeBuilder.create(BatteryBlockEntity::new, BATTERY_BLOCK).build();

  @Override
  public void onInitialize() {
    Registry.register(Registry.BLOCK, new Identifier(NAMESPACE, "test_battery"), BATTERY_BLOCK);
    Registry.register(
        Registry.BLOCK_ENTITY_TYPE, new Identifier(NAMESPACE, "test_battery"), BATTERY_BLOCK_ENTITY);
    Registry.register(Registry.ITEM, new Identifier(NAMESPACE, "test_battery_item"), BATTERY_ITEM);
    EnergyStorage.SIDED.registerForBlockEntity(
        (battery, side) -> battery.energy, BATTERY_BLOCK_ENTITY);
    // SimpleEnergyItem implementations get their EnergyStorage.ITEM provider from TR Energy itself.
  }

  public static ItemStack chargedItem(long amount) {
    ItemStack stack = new ItemStack(BATTERY_ITEM);
    SimpleEnergyItem.setStoredEnergyUnchecked(stack, amount);
    return stack;
  }

  public static long stored(ItemStack stack) {
    return SimpleEnergyItem.getStoredEnergyUnchecked(stack);
  }

  private static final class BatteryBlock extends Block implements BlockEntityProvider {
    BatteryBlock() {
      super(AbstractBlock.Settings.of(Material.METAL));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
      return new BatteryBlockEntity(pos, state);
    }
  }

  public static final class BatteryBlockEntity extends BlockEntity {
    public final SimpleEnergyStorage energy =
        new SimpleEnergyStorage(CAPACITY, TRANSFER, TRANSFER) {
          @Override
          protected void onFinalCommit() {
            markDirty();
          }
        };

    BatteryBlockEntity(BlockPos pos, BlockState state) {
      super(BATTERY_BLOCK_ENTITY, pos, state);
    }
  }

  private static final class BatteryItem extends Item implements SimpleEnergyItem {
    BatteryItem() {
      super(new Item.Settings().maxCount(1));
    }

    @Override
    public long getEnergyCapacity(ItemStack stack) {
      return CAPACITY;
    }

    @Override
    public long getEnergyMaxInput(ItemStack stack) {
      return TRANSFER;
    }

    @Override
    public long getEnergyMaxOutput(ItemStack stack) {
      return TRANSFER;
    }
  }
}
