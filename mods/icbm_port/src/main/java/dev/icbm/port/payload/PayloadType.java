package dev.icbm.port.payload;

import java.util.Locale;

/** Registry catalog adapted from ICBM Classic ExplosiveInit (MIT). */
public enum PayloadType {
  CONDENSED,
  SHRAPNEL,
  INCENDIARY,
  DEBILITATION,
  CHEMICAL,
  ANVIL,
  REPULSIVE,
  ATTRACTIVE,
  FRAGMENTATION,
  CONTAGIOUS,
  SONIC,
  BREACHING,
  THERMOBARIC,
  NUCLEAR,
  EMP,
  EXOTHERMIC,
  ENDOTHERMIC,
  ANTI_GRAVITATIONAL("antigravitational"),
  ENDER,
  HYPERSONIC("hypersonic", false),
  ANTIMATTER,
  REDMATTER,
  MISSILEMODULE("missile", false),
  ROT("rot", false),
  MUTATION("mutation", false),
  COLOR("colors"),
  SMOKE;

  private final String id;
  private final boolean content;

  PayloadType() {
    this.id = name().toLowerCase(Locale.ROOT);
    this.content = true;
  }

  PayloadType(String id) {
    this(id, true);
  }

  PayloadType(String id, boolean content) {
    this.id = id;
    this.content = content;
  }

  public String id() {
    return id;
  }

  public int tier() {
    return switch (this) {
      case CONDENSED,
              SHRAPNEL,
              INCENDIARY,
              DEBILITATION,
              CHEMICAL,
              ANVIL,
              REPULSIVE,
              ATTRACTIVE,
              COLOR,
              SMOKE ->
          1;
      case FRAGMENTATION, CONTAGIOUS, SONIC, BREACHING, THERMOBARIC -> 2;
      case NUCLEAR, EMP, EXOTHERMIC, ENDOTHERMIC, ANTI_GRAVITATIONAL, ENDER -> 3;
      case ANTIMATTER, REDMATTER -> 4;
      default -> 0;
    };
  }

  public int blockFuseTicks() {
    return PayloadConfig.forType(this).blockFuseTicks();
  }

  int defaultBlockFuseTicks() {
    return switch (this) {
      case CONDENSED, BREACHING -> 0;
      case REPULSIVE, ATTRACTIVE -> 120;
      case ANTIMATTER -> 300;
      default -> 100;
    };
  }

  public int cartFuseTicks() {
    return PayloadConfig.forType(this).cartFuseTicks();
  }

  int defaultCartFuseTicks() {
    return this == CONDENSED ? 100 : defaultBlockFuseTicks();
  }

  public int grenadeFuseTicks() {
    return PayloadConfig.forType(this).grenadeFuseTicks();
  }

  int defaultGrenadeFuseTicks() {
    return this == REPULSIVE || this == ATTRACTIVE ? 120 : 100;
  }

  public boolean hasContent() {
    return content;
  }

  public static PayloadType fromId(String id) {
    for (PayloadType type : values()) if (type.id.equals(id)) return type;
    return CONDENSED;
  }
}
