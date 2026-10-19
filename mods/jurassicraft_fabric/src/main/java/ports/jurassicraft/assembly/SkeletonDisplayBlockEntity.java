package ports.jurassicraft.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.content.Species;

public final class SkeletonDisplayBlockEntity extends BlockEntity {
    private Species species = Species.VELOCIRAPTOR;
    private boolean fresh;
    private int gender;
    private int pose;
    public SkeletonDisplayBlockEntity(BlockPos pos, BlockState state) { super(AssemblyRegistry.DISPLAY_ENTITY.get(), pos, state); }
    public Species species() { return species; }
    public boolean fresh() { return fresh; }
    public int gender() { return gender; }
    public int pose() { return pose; }
    public void chooseRandomGender() {
        if (gender == 0 && level != null) {
            gender = level.random.nextBoolean() ? 1 : 2;
            setChanged();
        }
    }
    static Species parseSpecies(String value) {
        try { return Species.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Species.VELOCIRAPTOR; }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Species", species.id());
        tag.putBoolean("Fresh", fresh);
        tag.putInt("Gender", gender);
        tag.putInt("Pose", pose);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        species = parseSpecies(tag.getString("Species"));
        fresh = tag.getBoolean("Fresh");
        gender = Math.max(0, Math.min(tag.getInt("Gender"), 2));
        pose = species == Species.TYRANNOSAURUS ? Math.max(0, Math.min(tag.getInt("Pose"), 2)) : 0;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
