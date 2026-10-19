package ports.jurassicraft.figure;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.content.Species;

public final class ActionFigureBlockEntity extends BlockEntity {
    private Species species = Species.BRACHIOSAURUS;
    private int gender;
    private float rotation;
    public ActionFigureBlockEntity(BlockPos pos, BlockState state) { super(ActionFigureRegistry.ENTITY.get(), pos, state); }
    public Species species() { return species; }
    public int gender() { return gender; }
    public float rotation() { return rotation; }
    public static Species parseSpecies(String name) {
        try { return Species.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Species.BRACHIOSAURUS; }
    }
    void place(float yaw) {
        rotation = Mth.wrapDegrees(180 - (int) yaw);
        if (gender == 0) gender = level.random.nextBoolean() ? 1 : 2;
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Species", species.id()); tag.putInt("Gender", gender); tag.putFloat("Rotation", rotation);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        species = parseSpecies(tag.getString("Species"));
        gender = Mth.clamp(tag.getInt("Gender"), 0, 2);
        float value = tag.getFloat("Rotation");
        rotation = Float.isFinite(value) ? Mth.wrapDegrees(value) : 0;
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
