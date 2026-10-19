package ports.jurassicraft.entity;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import ports.jurassicraft.content.Species;

/** Dimension-scoped family records survive members' chunks being unloaded independently. */
public final class DinosaurFamilyData extends SavedData {
    private static final String DATA_NAME = "jurassicraft_dinosaur_families";
    private final Map<UUID, Family> families = new HashMap<>();
    private final Map<UUID, UUID> memberships = new HashMap<>();

    static final class Family {
        final UUID id;
        final Species species;
        final Set<UUID> parents = new LinkedHashSet<>();
        final Set<UUID> children = new LinkedHashSet<>();
        BlockPos home;
        long stayHomeUntil;
        Family(UUID id, Species species) { this.id = id; this.species = species; }
    }

    static DinosaurFamilyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DinosaurFamilyData::load, DinosaurFamilyData::new, DATA_NAME);
    }

    static DinosaurFamilyData load(CompoundTag tag) {
        DinosaurFamilyData data = new DinosaurFamilyData();
        ListTag entries = tag.getList("Families", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            Species species;
            try { species = Species.valueOf(entry.getString("Species")); }
            catch (IllegalArgumentException exception) { continue; }
            Family family = new Family(entry.getUUID("Id"), species);
            if (data.families.containsKey(family.id)) continue;
            readMembers(entry.getList("Parents", Tag.TAG_COMPOUND), family.parents, data.memberships, family.id, 2);
            if (family.parents.isEmpty()) continue;
            readMembers(entry.getList("Children", Tag.TAG_COMPOUND), family.children, data.memberships, family.id, Integer.MAX_VALUE);
            if (entry.contains("Home", Tag.TAG_LONG)) family.home = BlockPos.of(entry.getLong("Home"));
            family.stayHomeUntil = Math.max(0, entry.getLong("StayHomeUntil"));
            data.families.put(family.id, family);
        }
        return data;
    }

    private static void readMembers(ListTag list, Set<UUID> into, Map<UUID, UUID> index, UUID id, int max) {
        for (int i = 0; i < list.size() && into.size() < max; i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            UUID member = entry.getUUID("Id");
            if (index.putIfAbsent(member, id) == null) into.add(member);
        }
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Family family : families.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", family.id);
            entry.putString("Species", family.species.name());
            entry.put("Parents", writeMembers(family.parents));
            entry.put("Children", writeMembers(family.children));
            if (family.home != null) entry.putLong("Home", family.home.asLong());
            entry.putLong("StayHomeUntil", family.stayHomeUntil);
            entries.add(entry);
        }
        tag.put("Families", entries);
        return tag;
    }

    private static ListTag writeMembers(Set<UUID> members) {
        ListTag list = new ListTag();
        for (UUID member : members) {
            CompoundTag entry = new CompoundTag(); entry.putUUID("Id", member); list.add(entry);
        }
        return list;
    }

    @Nullable Family family(UUID member) { return families.get(memberships.get(member)); }

    @Nullable Family form(DinosaurEntity first, DinosaurEntity second) {
        if (first == second || first.level != second.level || first.species() != second.species()
                || first.isMale() == second.isMale() || !first.isAlive() || !second.isAlive()
                || first.isCarcass() || second.isCarcass() || family(first.getUUID()) != null || family(second.getUUID()) != null) return null;
        Family family = new Family(UUID.randomUUID(), first.species());
        family.parents.add(first.getUUID()); family.parents.add(second.getUUID());
        family.home = first.blockPosition();
        families.put(family.id, family);
        memberships.put(first.getUUID(), family.id); memberships.put(second.getUUID(), family.id);
        setDirty();
        return family;
    }

    /** Join a hatchling only if the saved family still exists in this dimension and has the same species. */
    public static void joinHatchling(DinosaurEntity baby, CompoundTag eggData) {
        if (!(baby.level instanceof ServerLevel level) || !eggData.hasUUID("Family")) return;
        DinosaurFamilyData data = get(level);
        data.addChild(eggData.getUUID("Family"), baby);
    }

    void addChild(UUID familyId, DinosaurEntity child) {
        Family family = families.get(familyId);
        if (family == null || family.species != child.species() || family.species == Species.BRACHIOSAURUS || child.growth() > .5f
                || family(child.getUUID()) != null) return;
        family.children.add(child.getUUID());
        memberships.put(child.getUUID(), family.id);
        setDirty();
    }

    void setHome(Family family, BlockPos home, long until) {
        family.home = home.immutable(); family.stayHomeUntil = until; setDirty();
    }

    void reanchorIfNeeded(DinosaurEntity member, Family family) {
        if (family.parents.isEmpty() || !family.parents.iterator().next().equals(member.getUUID())) return;
        if (family.home == null || family.home.distSqr(member.blockPosition()) > 4096)
            setHome(family, member.blockPosition(), family.stayHomeUntil);
    }

    boolean mayBreed(DinosaurEntity first, DinosaurEntity second) {
        Family family = family(first.getUUID()), other = family(second.getUUID());
        if (family == null && other == null) return true; // Explicit/manual breeding remains supported.
        if (family == null || family != other || !family.parents.contains(first.getUUID())
                || !family.parents.contains(second.getUUID())) return false;
        prune((ServerLevel) first.level, family);
        return family.species == Species.BRACHIOSAURUS || family.species == Species.COELACANTH || family.children.isEmpty();
    }

    @Nullable DinosaurEntity mate(DinosaurEntity dinosaur) {
        if (!(dinosaur.level instanceof ServerLevel level)) return null;
        Family family = family(dinosaur.getUUID());
        if (family == null || !family.parents.contains(dinosaur.getUUID())) return null;
        prune(level, family);
        for (UUID parent : family.parents) if (!parent.equals(dinosaur.getUUID())) {
            Entity entity = level.getEntity(parent);
            if (entity instanceof DinosaurEntity partner && partner.isAlive() && !partner.isCarcass()) return partner;
        }
        return null;
    }

    /** Only known dead or mature members are removed; a null lookup can mean an unloaded chunk. */
    void prune(ServerLevel level, Family family) {
        for (UUID id : Set.copyOf(family.children)) {
            Entity entity = level.getEntity(id);
            if (entity instanceof DinosaurEntity d && (!d.isAlive() || d.isCarcass() || d.growth() > .5f)) remove(id);
        }
        for (UUID id : Set.copyOf(family.parents)) {
            Entity entity = level.getEntity(id);
            if (entity instanceof DinosaurEntity d && (!d.isAlive() || d.isCarcass())) remove(id);
        }
    }

    void remove(UUID member) {
        Family family = family(member);
        if (family == null) return;
        family.parents.remove(member); family.children.remove(member); memberships.remove(member);
        if (family.parents.isEmpty()) {
            family.parents.forEach(memberships::remove);
            family.children.forEach(memberships::remove);
            families.remove(family.id);
        }
        setDirty();
    }
}
