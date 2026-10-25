package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.delivery.BombCartEntity;
import dev.icbm.port.delivery.BombletEntity;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.delivery.GrenadeEntity;
import dev.icbm.port.delivery.HeldItemMissileEntity;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Explosive defuser adapted from MIT ICBM-Classic {@code content/items/ItemDefuser.java}. Left-click
 * an armed explosive to disarm it: entities with an explosive capability (primed charges, grenades,
 * bomblets, explosive missiles) drop their item and vanish, primed vanilla TNT drops a TNT block,
 * and bomb carts are recovered through {@link BombCartEntity#defuse()}.
 *
 * <p>Upstream extends {@code ItemICBMElectrical}, whose energy is permanently {@code
 * Integer.MAX_VALUE} and whose discharge is a no-op, so its 2000-energy cost never applies: the
 * defuser has no durability and never runs out. The port keeps that, keeps spectators out, and
 * additionally neutralizes its own guided interceptors and held-item missiles.
 */
public final class DefuserItem extends Item {
  DefuserItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  public static boolean canDefuse(Entity entity) {
    return entity instanceof SamInterceptorEntity
        || entity instanceof HeldItemMissileEntity
        || entity instanceof MissileEntity
        || entity instanceof PrimedExplosiveEntity
        || entity instanceof GrenadeEntity
        || entity instanceof BombletEntity
        || entity instanceof BombCartEntity
        || entity instanceof TntEntity;
  }

  /** Server-side disarm; returns false when the entity is not an explosive. */
  public static boolean defuse(Entity target) {
    if (target.world.isClient || target.isRemoved() || !canDefuse(target)) return false;
    if (target instanceof BombCartEntity cart) {
      cart.defuse();
      return true;
    }
    if (target instanceof TntEntity) target.dropItem(Items.TNT);
    else if (target instanceof PrimedExplosiveEntity explosive)
      target.dropStack(explosive.getStack().copy());
    else if (target instanceof MissileEntity missile) target.dropStack(missile.getStack().copy());
    else if (target instanceof GrenadeEntity grenade) target.dropStack(grenade.getStack().copy());
    else if (target instanceof BombletEntity bomblet) {
      Item item = DeliveryContent.BOMBLETS.get(bomblet.getPayload());
      if (item != null) target.dropStack(new ItemStack(item));
    }
    if (target instanceof HeldItemMissileEntity heldMissile) heldMissile.recover();
    else target.discard();
    return true;
  }

  public static ActionResult attack(PlayerEntity player, World world, Hand hand, Entity target) {
    if (player.isSpectator()) return ActionResult.PASS;
    if (!(player.getStackInHand(hand).getItem() instanceof DefuserItem) || !canDefuse(target))
      return ActionResult.PASS;
    if (!world.isClient) defuse(target);
    return ActionResult.SUCCESS;
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    tooltip.add(Text.translatable("tooltip.icbm_port.defuser").formatted(Formatting.GRAY));
  }
}
