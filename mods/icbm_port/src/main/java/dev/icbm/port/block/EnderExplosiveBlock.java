package dev.icbm.port.block;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Ender explosive that accepts a destination. Adapted from MIT ICBM-Classic {@code
 * content/reg/ExplosiveInit.enderBlockCoordSet} and {@code blast/ender/EnderBlastCustomization}:
 * using a tool that holds a recorded target (the targeting tool or radar gun here, upstream's GPS
 * device) on the placed block stores that position and dimension; the primed explosive carries it
 * to the blast.
 */
public final class EnderExplosiveBlock extends CondensedExplosiveBlock implements BlockEntityProvider {
  public static BlockEntityType<Target> TARGET;

  public EnderExplosiveBlock(Settings settings) {
    super(settings, PayloadType.ENDER);
  }

  public static void register(EnderExplosiveBlock block) {
    TARGET =
        Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            IcbmPort.id("ender_explosive"),
            FabricBlockEntityTypeBuilder.create(Target::new, block).build());
  }

  @Override
  public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return new Target(pos, state);
  }

  @Override
  public ActionResult onUse(
      BlockState state,
      World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockHitResult hit) {
    ItemStack stack = player.getStackInHand(hand);
    NbtCompound recorded = stack.getSubNbt("IcbmTarget");
    if (recorded == null || player.isSpectator()) return super.onUse(state, world, pos, player, hand, hit);
    if (!world.isClient) {
      Identifier dimension = Identifier.tryParse(recorded.getString("Dimension"));
      if (recorded.contains("Position") && dimension != null
          && world.getBlockEntity(pos) instanceof Target target) {
        target.set(dimension, BlockPos.fromLong(recorded.getLong("Position")));
        player.sendMessage(
            Text.translatable(
                "message.icbm_port.ender_target_set", target.position.toShortString()),
            true);
      } else
        player.sendMessage(Text.translatable("message.icbm_port.ender_target_missing"), true);
    }
    return ActionResult.success(world.isClient);
  }

  @Override
  protected void configurePrimed(
      BlockState state, @Nullable BlockEntity blockEntity, PrimedExplosiveEntity entity) {
    if (blockEntity instanceof Target target && target.position != null)
      entity.setEnderTarget(target.dimension, target.position);
  }

  /** Holds the stored destination of one placed ender explosive. */
  public static final class Target extends BlockEntity {
    private @Nullable Identifier dimension;
    private @Nullable BlockPos position;

    public Target(BlockPos pos, BlockState state) {
      super(TARGET, pos, state);
    }

    public @Nullable BlockPos position() {
      return position;
    }

    public @Nullable Identifier dimension() {
      return dimension;
    }

    public void set(Identifier dimension, BlockPos position) {
      this.dimension = dimension;
      this.position = position.toImmutable();
      markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
      if (position != null && dimension != null) {
        nbt.putLong("Position", position.asLong());
        nbt.putString("Dimension", dimension.toString());
      }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
      position = nbt.contains("Position") ? BlockPos.fromLong(nbt.getLong("Position")) : null;
      dimension = position == null ? null : Identifier.tryParse(nbt.getString("Dimension"));
      if (dimension == null) position = null;
    }
  }
}
