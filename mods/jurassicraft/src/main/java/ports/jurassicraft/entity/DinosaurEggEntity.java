package ports.jurassicraft.entity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.DinosaurAttributes;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.hatching.HatcheryRegistry;

/** A naturally laid egg hatches after 5,000–6,000 loaded ticks, or can be picked up. */
public final class DinosaurEggEntity extends Entity {
    private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(DinosaurEggEntity.class, EntityDataSerializers.STRING);
    private String genetics = "AAAAAAAAAA";
    private int quality = 100;
    private boolean male;
    private DinosaurAttributes inherited;
    private UUID family;
    private int hatchTime = 5500;

    public DinosaurEggEntity(EntityType<? extends DinosaurEggEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() { entityData.define(SPECIES, Species.TYRANNOSAURUS.id()); }
    public Species species() {
        Species result = DNAData.findDinosaur(entityData.get(SPECIES));
        return result == null || result == Species.COELACANTH ? Species.TYRANNOSAURUS : result;
    }
    public void setEmbryo(Species species, String genome, int quality, boolean male) {
        setEmbryo(species, genome, quality, male, null);
    }
    public void setEmbryo(Species species, String genome, int quality, boolean male,
            @javax.annotation.Nullable DinosaurAttributes inherited) {
        if (species == Species.COELACANTH || !DNAData.validGenetics(genome)) throw new IllegalArgumentException("Invalid egg embryo");
        entityData.set(SPECIES, species.id());
        genetics = genome;
        this.quality = net.minecraft.util.Mth.clamp(quality, 0, 100);
        this.male = male;
        this.inherited = inherited;
        hatchTime = 5000 + random.nextInt(1001);
    }
    void setFamily(UUID family) { this.family = family; }
    @Override public void tick() {
        super.tick();
        if (!level.isClientSide) {
            if (hatchTime > 0) hatchTime--;
            if (hatchTime == 0 && tickCount % 20 == 0 && level instanceof ServerLevel server) {
                DinosaurEntity baby = DinosaurRegistry.spawn(species(), server, blockPosition(), true, male, inherited);
                if (baby != null) {
                    baby.setGenetics(genetics);
                    baby.setDNAQuality(quality);
                    if (family != null) {
                        CompoundTag familyTag = new CompoundTag(); familyTag.putUUID("Family", family);
                        DinosaurFamilyData.joinHatchling(baby, familyTag);
                    }
                    discard();
                    return;
                }
            }
            if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -.035, 0));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.85));
        }
    }
    @Override public boolean isPickable() { return true; }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            ItemStack item = new ItemStack(HatcheryRegistry.hatchedEgg(species()));
            Genetics.write(item.getOrCreateTag(), DNAData.dinosaur(species(), quality, genetics));
            item.getOrCreateTag().putBoolean("Gender", male);
            if (family != null) item.getTag().putUUID("Family", family);
            if (inherited != null) {
                CompoundTag attributes = new CompoundTag();
                inherited.write(attributes);
                item.getTag().put("GeneticAttributes", attributes);
            }
            if (!player.getInventory().add(item)) spawnAtLocation(item);
            discard();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Species", species().id()); tag.putString("Genetics", genetics);
        tag.putInt("DNAQuality", quality); tag.putBoolean("Male", male); tag.putInt("HatchTime", hatchTime);
        if (family != null) tag.putUUID("Family", family);
        if (inherited != null) {
            CompoundTag attributes = new CompoundTag();
            inherited.write(attributes);
            tag.put("GeneticAttributes", attributes);
        } else tag.remove("GeneticAttributes");
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        Species species = DNAData.findDinosaur(tag.getString("Species"));
        if (species == null || species == Species.COELACANTH) species = Species.TYRANNOSAURUS;
        entityData.set(SPECIES, species.id());
        if (DNAData.validGenetics(tag.getString("Genetics"))) genetics = tag.getString("Genetics");
        quality = net.minecraft.util.Mth.clamp(tag.getInt("DNAQuality"), 0, 100);
        male = tag.getBoolean("Male"); hatchTime = Math.max(0, tag.getInt("HatchTime"));
        family = tag.hasUUID("Family") ? tag.getUUID("Family") : null;
        inherited = DinosaurAttributes.read(tag.getCompound("GeneticAttributes")).orElse(null);
    }
    @Override public Packet<?> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
