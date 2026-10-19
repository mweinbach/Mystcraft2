package ports.jurassicraft.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The original social score rises by two for nearby herd members every ten ticks. */
final class DinosaurFamilyBrain {
    private final DinosaurEntity dinosaur;
    // Bound historical contacts without treating an unloaded animal as dead.
    private final Map<UUID, Integer> relationships = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) { return size() > 128; }
    };
    DinosaurFamilyBrain(DinosaurEntity dinosaur) { this.dinosaur = dinosaur; }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        relationships.forEach((id, score) -> {
            CompoundTag relationship = new CompoundTag();
            relationship.putUUID("UUID", id); relationship.putInt("Score", score);
            list.add(relationship);
        });
        tag.put("Relationships", list);
    }

    void load(CompoundTag tag) {
        relationships.clear();
        ListTag list = tag.getList("Relationships", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag relationship = list.getCompound(i);
            if (relationship.hasUUID("UUID") && relationship.contains("Score", Tag.TAG_ANY_NUMERIC))
                relationships.put(relationship.getUUID("UUID"), Mth.clamp(relationship.getInt("Score"), -1000, 1000));
        }
    }

    void tick() {
        if (!(dinosaur.level instanceof ServerLevel level) || dinosaur.tickCount % 10 != 0) return;
        assistYoung(dinosaur);
        DinosaurFamilyData data = DinosaurFamilyData.get(level);
        DinosaurFamilyData.Family family = data.family(dinosaur.getUUID());
        if (family != null) {
            data.prune(level, family);
            data.reanchorIfNeeded(dinosaur, family);
            return;
        }
        if (dinosaur.isCarcass() || !dinosaur.isAlive() || dinosaur.getTarget() != null) return;
        for (DinosaurEntity other : level.getEntitiesOfClass(DinosaurEntity.class, dinosaur.getBoundingBox().inflate(16, 5, 16),
                other -> other != dinosaur && other.isAlive() && !other.isCarcass() && other.species() == dinosaur.species()
                        && (dinosaur.isAquatic() || dinosaur.herdOrNull() != null && dinosaur.herdOrNull() == other.herdOrNull()))) {
            int score = relationships.getOrDefault(other.getUUID(), 0);
            if (dinosaur.distanceToSqr(other) < 32) score = Math.min(1000, score + 2);
            else if (score > 0 && dinosaur.getRandom().nextInt(5) == 0) score--;
            relationships.put(other.getUUID(), score);
            if (score > 900 && dinosaur.getRandom().nextInt(10) == 0 && tryBond(other)) break;
        }
    }

    boolean tryBond(DinosaurEntity other) {
        if (!(dinosaur.level instanceof ServerLevel level) || relationships.getOrDefault(other.getUUID(), 0) <= 900
                || dinosaur.getTarget() != null || DinosaurFamilyData.get(level).form(dinosaur, other) == null) return false;
        relationships.put(other.getUUID(), 1000);
        int delay = 1000 + dinosaur.getRandom().nextInt(1000);
        dinosaur.delayFamilyBreeding(delay); other.delayFamilyBreeding(delay);
        return true;
    }

    void attackedBy(LivingEntity attacker, float amount) {
        if (attacker instanceof DinosaurEntity && attacker != dinosaur) {
            relationships.compute(attacker.getUUID(), (id, score) -> Math.max(-1000, (score == null ? 0 : score) - (int) amount));
        }
    }

    static void defendYoung(DinosaurEntity young, LivingEntity attacker) {
        if (!(young.level instanceof ServerLevel level) || young.growth() > .5f || !attacker.isAlive()
                || attacker instanceof Player player && player.isCreative()) return;
        for (DinosaurEntity adult : level.getEntitiesOfClass(DinosaurEntity.class, young.getBoundingBox().inflate(8, 3, 8),
                other -> other != young && other != attacker && other.species() == young.species()
                        && other.growth() > .75f && other.isAlive() && !other.isCarcass() && !other.isTranquilized())) {
            if (adult.canAttack(attacker)) adult.defendInfant(attacker);
        }
    }

    /** Upstream adults also assist any nearby conspecific infant that already has a target. */
    static void assistYoung(DinosaurEntity adult) {
        if (!(adult.level instanceof ServerLevel level) || adult.growth() <= .75f || !adult.isAlive()
                || adult.isCarcass() || adult.isTranquilized()) return;
        for (DinosaurEntity young : level.getEntitiesOfClass(DinosaurEntity.class, adult.getBoundingBox().inflate(8, 3, 8),
                other -> other != adult && other.species() == adult.species() && other.growth() <= .5f && other.isAlive())) {
            LivingEntity target = young.getTarget();
            if (target != null && target != adult && target.isAlive() && !(target instanceof Player player && player.isCreative())
                    && adult.canAttack(target)) {
                if (adult.getTarget() != target) adult.defendInfant(target);
                return;
            }
        }
    }
}
