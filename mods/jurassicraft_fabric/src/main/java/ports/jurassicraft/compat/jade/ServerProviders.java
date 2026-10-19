package ports.jurassicraft.compat.jade;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.hatching.HatcheryBlockEntity;
import snownee.jade.api.IServerDataProvider;

public final class ServerProviders {
    public static final String DINOSAUR_DATA = "JurassicDinosaur", FEEDER_DATA = "JurassicFeeder", INCUBATOR_DATA = "JurassicIncubator";
    public static final ResourceLocation DINOSAUR_ID = id("dinosaur"), FEEDER_ID = id("feeder"), INCUBATOR_ID = id("incubator");
    private ServerProviders() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    public static final IServerDataProvider<Entity> DINOSAUR = new IServerDataProvider<>() {
        @Override public ResourceLocation getUid() { return DINOSAUR_ID; }
        @Override public void appendServerData(CompoundTag tag, ServerPlayer player, Level level, Entity entity, boolean details) {
            if (!(entity instanceof DinosaurEntity dinosaur)) return;
            String owner = dinosaur.getOwnerUUID() == null || level.getServer() == null ? null
                    : level.getServer().getProfileCache().get(dinosaur.getOwnerUUID()).map(GameProfile::getName).orElse(null);
            tag.put(DINOSAUR_DATA, InspectionData.dinosaur(dinosaur, owner));
        }
    };
    public static final IServerDataProvider<BlockEntity> FEEDER = new IServerDataProvider<>() {
        @Override public ResourceLocation getUid() { return FEEDER_ID; }
        @Override public void appendServerData(CompoundTag tag, ServerPlayer player, Level level, BlockEntity entity, boolean details) {
            if (entity instanceof FeederBlockEntity feeder) tag.put(FEEDER_DATA, InspectionData.feeder(feeder));
        }
    };
    public static final IServerDataProvider<BlockEntity> INCUBATOR = new IServerDataProvider<>() {
        @Override public ResourceLocation getUid() { return INCUBATOR_ID; }
        @Override public void appendServerData(CompoundTag tag, ServerPlayer player, Level level, BlockEntity entity, boolean details) {
            if (entity instanceof HatcheryBlockEntity hatchery) {
                CompoundTag data = InspectionData.incubator(hatchery);
                if (!data.isEmpty()) tag.put(INCUBATOR_DATA, data);
            }
        }
    };
}
