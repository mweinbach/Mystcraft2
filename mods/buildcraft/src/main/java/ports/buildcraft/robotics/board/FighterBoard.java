package ports.buildcraft.robotics.board;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.lib.Power;
import ports.buildcraft.robotics.Progress;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.WorkArea;

/**
 * Boards that fight, written for this port: the knight fights hostile mobs with a sword and the butcher fights adult
 * animals with a sword or an axe. The robot takes its weapon from the inventories beside its home station, flies to
 * the nearest prey in its work area and strikes it from just above as its owner's fake player, so the loader's attack
 * event lets claim and protection mods refuse it ({@link BlockProtection#mayAttack}); prey refused once is left alone
 * until the robot next rests. A strike deals the weapon's attack damage (with its enchantment bonus against that
 * mob), costs {@link #HIT_COST}, comes as often as the weapon's attack speed allows and wears the weapon as a
 * player's strike would. Tamed and named mobs are spared. What the prey drops stays where it falls (a picker
 * collects it); with nothing to fight the robot rests at home.
 */
public class FighterBoard extends RobotBoard {
    public static final int IDLE_TICKS = 40;
    public static final long HIT_COST = 50 * Power.MJ;
    /** How far from where it means to hover the robot may be and still strike. */
    private static final double REACH = 1.0;

    private final Predicate<LivingEntity> prey;
    private final Predicate<ItemStack> weapons;
    @Nullable
    private LivingEntity target;
    private int cooldown;
    private boolean unloading;
    /** Prey protection would not let the robot attack, left alone until it next rests. */
    private final Set<UUID> refused = new HashSet<>();

    protected FighterBoard(RobotEntity robot, Predicate<LivingEntity> prey, Predicate<ItemStack> weapons) {
        super(robot);
        this.prey = prey;
        this.weapons = weapons;
    }

    public static FighterBoard knight(RobotEntity robot) {
        return new FighterBoard(robot, FighterBoard::isMonster, FighterBoard::isSword);
    }

    public static FighterBoard butcher(RobotEntity robot) {
        return new FighterBoard(robot, FighterBoard::isLivestock, stack -> isSword(stack) || isAxe(stack));
    }

    public static boolean isMonster(LivingEntity entity) {
        return entity instanceof Enemy && !entity.hasCustomName();
    }

    /** Adult animals that nobody has tamed or named. */
    public static boolean isLivestock(LivingEntity entity) {
        if (!(entity instanceof Animal animal) || animal.isBaby() || animal.hasCustomName()) return false;
        return !(animal instanceof TamableAnimal tamable && tamable.isTame());
    }

    public static boolean isSword(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.getItem() instanceof AxeItem;
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;
        if (unloading) {
            Progress progress = unloadAtHome();
            if (progress == Progress.WORKING) return;
            unloading = false;
            if (progress == Progress.FAILED) robot.sleep(IDLE_TICKS);
            return;
        }
        if (!haveTool(weapons)) return;
        WorkArea area = robot.workArea();
        if (!isValid(target, area)) {
            drop();
            target = find(area);
            if (target == null) {
                if (!robot.isInventoryEmpty()) unloading = true;
                else if (idleAtHome(IDLE_TICKS)) refused.clear();
                return;
            }
            world().claim(robot, target.getUUID());
        }
        Vec3 spot = target.position().add(0, target.getBbHeight() + 0.25, 0);
        robot.flyTowards(spot);
        if (cooldown > 0 || robot.position().distanceToSqr(spot) > REACH * REACH) return;
        strike(target);
    }

    private void strike(LivingEntity victim) {
        ServerPlayer player = robot.actor();
        if (!BlockProtection.mayAttack(player, victim)) {
            refused.add(victim.getUUID());
            drop();
            return;
        }
        if (!robot.spend(HIT_COST)) return;
        ItemStack weapon = robot.tool();
        cooldown = attackTicks(weapon);
        if (victim.hurt(DamageSource.playerAttack(player), attackDamage(weapon, victim))) robot.damageTool(isSword(weapon) ? 1 : 2);
    }

    /** A player's attack damage with {@code weapon} against {@code victim}: one, plus the weapon's, plus its enchantments'. */
    public static float attackDamage(ItemStack weapon, LivingEntity victim) {
        return (float) (1 + modifiers(weapon, Attributes.ATTACK_DAMAGE)) + EnchantmentHelper.getDamageBonus(weapon, victim.getMobType());
    }

    /** Ticks between strikes: a player's full swing with {@code weapon}, and never so quick that the victim is still invulnerable. */
    public static int attackTicks(ItemStack weapon) {
        double speed = 4 + modifiers(weapon, Attributes.ATTACK_SPEED);
        return Math.max(11, Mth.ceil(20 / Math.max(0.1, speed)));
    }

    private static double modifiers(ItemStack weapon, Attribute attribute) {
        double total = 0;
        for (AttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) total += modifier.getAmount();
        }
        return total;
    }

    private boolean isValid(@Nullable LivingEntity entity, WorkArea area) {
        return entity != null && entity.isAlive() && !entity.isInvulnerable() && area.contains(entity.position())
                && !refused.contains(entity.getUUID()) && prey.test(entity) && !world().isClaimedByOther(robot, entity.getUUID());
    }

    @Nullable
    private LivingEntity find(WorkArea area) {
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, area.bounds(), entity -> isValid(entity, area));
        return found.stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(robot))).orElse(null);
    }

    private void drop() {
        if (target != null) world().release(robot, target.getUUID());
        target = null;
    }

    @Override
    protected void cargoToUnload() {
        unloading = true;
    }

    @Override
    public void reset() {
        drop();
        unloading = false;
    }
}
