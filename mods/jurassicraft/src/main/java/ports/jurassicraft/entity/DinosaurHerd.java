package ports.jurassicraft.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import ports.jurassicraft.content.Species;

/** Ephemeral same-species groups, rebuilt from nearby loaded animals like the original herds. */
public final class DinosaurHerd {
    private final Set<DinosaurEntity> members = new LinkedHashSet<>();
    private final Map<LivingEntity, Long> enemies = new LinkedHashMap<>();
    private boolean fleeing;

    DinosaurHerd(DinosaurEntity first) { members.add(first); first.setHerd(this); }

    public static int maxSize(Species species) {
        return switch (species) {
            case VELOCIRAPTOR -> 18;
            case COELACANTH -> 1;
            case MICRORAPTOR -> 16;
            case BRACHIOSAURUS -> 4;
            case MUSSAURUS -> 20;
            case DILOPHOSAURUS -> 10;
            case TYRANNOSAURUS -> 3;
            default -> 32;
        };
    }
    static double attackBias(Species species) {
        return switch (species) {
            case VELOCIRAPTOR -> 600;
            case COELACANTH -> 100;
            case MICRORAPTOR, TRICERATOPS -> 400;
            case BRACHIOSAURUS, DILOPHOSAURUS -> 1200;
            case MUSSAURUS -> -500;
            case PARASAUROLOPHUS -> -100;
            case TYRANNOSAURUS -> 1000;
            default -> 200;
        };
    }
    static boolean alwaysFlee(Species species) {
        return species == Species.COELACANTH || species == Species.MICRORAPTOR
                || species == Species.MUSSAURUS || species == Species.GALLIMIMUS;
    }
    static boolean flocks(Species species) { return species != Species.COELACANTH && species != Species.MICRORAPTOR; }
    static double flockSpeed(Species species) {
        return switch (species) {
            case MUSSAURUS -> 1.25;
            case GALLIMIMUS -> 1.4;
            case PARASAUROLOPHUS -> 1.5;
            default -> .8;
        };
    }
    DinosaurEntity leader() { return members.isEmpty() ? null : members.iterator().next(); }
    int size() { return members.size(); }
    boolean hasEnemies() { return !enemies.isEmpty(); }
    boolean isFleeing() { return fleeing && hasEnemies(); }
    boolean isBusyForRest() {
        return hasEnemies() || members.stream().anyMatch(member -> !member.getNavigation().isDone());
    }
    LivingEntity nearestEnemy(DinosaurEntity member) {
        return enemies.keySet().stream().filter(e -> e.isAlive() && !e.isRemoved())
                .min(java.util.Comparator.comparingDouble(member::distanceToSqr)).orElse(null);
    }
    void remove(DinosaurEntity member) {
        members.remove(member);
        if (member.herdOrNull() == this) member.setHerd(null);
    }
    boolean merge(DinosaurHerd other) {
        DinosaurEntity a = leader(), b = other.leader();
        if (a == null || b == null || other == this || a.level != b.level || a.species() != b.species()
                || size() + other.size() > maxSize(a.species())) return false;
        for (DinosaurEntity member : new ArrayList<>(other.members)) {
            other.members.remove(member);
            members.add(member);
            member.setHerd(this);
        }
        enemies.putAll(other.enemies);
        fleeing |= other.fleeing;
        enemies.keySet().removeIf(members::contains);
        if (!enemies.isEmpty()) alert(enemies.keySet().iterator().next());
        else fleeing = false;
        return true;
    }
    void refresh() {
        DinosaurEntity lead = leader();
        if (lead == null) return;
        for (DinosaurEntity member : new ArrayList<>(members)) {
            if (!member.isAlive() || member.isRemoved() || member.level != lead.level
                    || member.isStarving() || member.isDehydrated() || member.distanceToSqr(lead) > 2048) remove(member);
        }
        lead = leader();
        if (lead == null) return;
        DinosaurEntity anchor = lead;
        long now = lead.level.getGameTime();
        enemies.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved()
                || entry.getKey().level != anchor.level || entry.getKey().distanceToSqr(anchor) > 1024
                || (entry.getKey() instanceof Player player && player.isCreative()) || entry.getValue() < now
                || members.contains(entry.getKey()));
        if (enemies.isEmpty()) fleeing = false;
        if (maxSize(lead.species()) > 1) {
            for (DinosaurEntity other : lead.level.getEntitiesOfClass(DinosaurEntity.class,
                    lead.getBoundingBox().inflate(16, 5, 16), d -> d != anchor && d.isAlive()
                            && d.species() == anchor.species() && !d.isStarving() && !d.isDehydrated())) {
                DinosaurHerd group = other.herd();
                if (group != this && !merge(group)) considerOutcast(other,
                        lead.level.getGameRules().getBoolean(DinosaurGameRules.KILL_HERD_OUTCAST));
            }
        }
        for (DinosaurEntity member : new ArrayList<>(members)) {
            LivingEntity target = member.getTarget();
            if (target != null && !member.isOwnerDefenseTarget(target) && !members.contains(target) && !enemies.containsKey(target)) {
                alert(target);
                break;
            }
        }
    }
    void considerOutcast(DinosaurEntity other, boolean enabled) {
        DinosaurEntity lead = leader();
        if (!enabled || lead == null || size() < maxSize(lead.species()) || members.contains(other)
                || other.species() != lead.species() || enemies.containsKey(other)) return;
        switch (lead.species()) {
            case VELOCIRAPTOR, MICRORAPTOR, DILOPHOSAURUS, TYRANNOSAURUS -> alert(other);
            default -> { }
        }
    }
    void alert(LivingEntity attacker) {
        DinosaurEntity lead = leader();
        if (lead == null || attacker == null || !attacker.isAlive() || attacker.isRemoved() || attacker.level != lead.level
                || members.contains(attacker) || attacker instanceof Player player && player.isCreative()) return;
        if (attacker instanceof DinosaurEntity dinosaur && dinosaur.herdOrNull() != null) {
            for (DinosaurEntity enemy : dinosaur.herdOrNull().members) addEnemy(enemy);
        } else addEnemy(attacker);
        double strength = members.stream().mapToDouble(DinosaurHerd::strength).sum()
                + attackBias(lead.species()) * members.size();
        double threat = enemies.keySet().stream().mapToDouble(DinosaurHerd::strength).sum();
        fleeing = alwaysFlee(lead.species()) || strength <= threat;
        for (DinosaurEntity member : members) {
            member.disturbSleep();
            if (fleeing) {
                member.setTarget(null);
            } else if (member.growth() > .5f && !member.isTranquilized() && member.canAttack(attacker)) {
                member.setTarget(attacker);
            }
        }
    }
    private void addEnemy(LivingEntity enemy) {
        DinosaurEntity lead = leader();
        if (enemy.isAlive() && !members.contains(enemy)) enemies.put(enemy, lead.level.getGameTime() + 600);
    }
    private static double strength(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attribute == null ? 0 : entity.getHealth() * attribute.getValue();
    }
}
