package dev.icbm.port.simulation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Damage dealt by a red matter core to anything that reaches it. Armour, shields and protection
 * enchantments do not help. It is not an out-of-world source, so invulnerable players (creative
 * and spectator) stay immune, as with other vanilla damage.
 */
public final class RedmatterDamage extends DamageSource {
  public static final String NAME = "icbm_port.redmatter";
  @Nullable private final Entity owner;

  public RedmatterDamage(@Nullable Entity owner) {
    super(NAME);
    this.owner = owner;
    setBypassesArmor();
    setUnblockable();
    setBypassesProtection();
  }

  // The owner is deliberately not reported as the attacker: the core is a force of nature once
  // armed, so it consumes its own owner too, does not hinge on PvP settings and does not make mobs
  // retaliate against whoever lit it. The owner only appears in the death message.
  @Nullable
  public Entity owner() {
    return owner;
  }

  /** "was consumed by red matter", or "by NAME's red matter" when the core has an owner. */
  @Override
  public Text getDeathMessage(LivingEntity entity) {
    String key = "death.attack." + NAME;
    return owner == null || owner == entity
        ? Text.translatable(key, entity.getDisplayName())
        : Text.translatable(key + ".player", entity.getDisplayName(), owner.getDisplayName());
  }
}
