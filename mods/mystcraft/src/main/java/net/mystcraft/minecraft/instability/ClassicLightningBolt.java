package net.mystcraft.minecraft.instability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.mystcraft.integration.platform.GameplayPlatform;

/** Source colored bolt: native striking behavior plus a synchronized, immutable-at-spawn RGB snapshot. */
public final class ClassicLightningBolt extends LightningBolt {
    private static final EntityDataAccessor<Float> RED=SynchedEntityData.defineId(ClassicLightningBolt.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GREEN=SynchedEntityData.defineId(ClassicLightningBolt.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BLUE=SynchedEntityData.defineId(ClassicLightningBolt.class,EntityDataSerializers.FLOAT);
    public ClassicLightningBolt(EntityType<? extends ClassicLightningBolt> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(RED,.45F);entityData.define(GREEN,.45F);entityData.define(BLUE,.5F);}
    public float red(){return entityData.get(RED);} public float green(){return entityData.get(GREEN);}public float blue(){return entityData.get(BLUE);}
    public void color(float red,float green,float blue){entityData.set(RED,channel(red));entityData.set(GREEN,channel(green));entityData.set(BLUE,channel(blue));}
    private static float channel(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("ClassicRed",red());tag.putFloat("ClassicGreen",green());tag.putFloat("ClassicBlue",blue());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);if(tag.contains("ClassicRed"))color(tag.getFloat("ClassicRed"),tag.getFloat("ClassicGreen"),tag.getFloat("ClassicBlue"));}
    @Override public Packet<?> getAddEntityPacket(){return GameplayPlatform.spawnPacket(this);}
}
