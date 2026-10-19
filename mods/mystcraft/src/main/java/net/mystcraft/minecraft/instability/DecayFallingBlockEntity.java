package net.mystcraft.minecraft.instability;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.DecayFallingRegistry;

/** State/BE-preserving classic falling adapter with transactional entity insertion and fallback drops. */
public final class DecayFallingBlockEntity extends FallingBlockEntity {
    private CompoundTag originalBlockEntity;
    private List<ItemStack> fallback=new ArrayList<>();
    public DecayFallingBlockEntity(EntityType<? extends DecayFallingBlockEntity> type,Level level) { super(type,level); dropItem=false; }
    public static boolean drop(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return false;
        if(level.getBlockState(pos).getMaterial().isLiquid()) return level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
        return dropEntity(level,pos)!=null;
    }
    public static DecayFallingBlockEntity dropEntity(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below()) || !level.isEmptyBlock(pos.below())) return null;
        BlockState state=level.getBlockState(pos); if(state.isAir()) return null;
        if(state.getMaterial().isLiquid()) return null;
        BlockEntity blockEntity=level.getBlockEntity(pos);
        CompoundTag saved=blockEntity==null?null:blockEntity.saveWithFullMetadata();
        DecayFallingBlockEntity falling=new DecayFallingBlockEntity(DecayFallingRegistry.FALLING.get(),level);
        CompoundTag entity=new CompoundTag(); entity.put("BlockState",NbtUtils.writeBlockState(state)); entity.putInt("Time",1); entity.putBoolean("DropItem",false);
        falling.readAdditionalSaveData(entity); falling.originalBlockEntity=saved;
        if(saved!=null && !state.getBlock().asItem().equals(net.minecraft.world.item.Items.AIR)) {
            ItemStack item=new ItemStack(state.getBlock()); item.getOrCreateTag().put("BlockEntityTag",saved.copy()); falling.fallback.add(item);
        } else falling.fallback.addAll(Block.getDrops(state,level,pos,blockEntity));
        falling.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); falling.setStartPos(pos);
        if(!level.addFreshEntity(falling)) { falling.discard(); return null; }
        if(!level.getBlockState(pos).equals(state) || saved!=null && (level.getBlockEntity(pos)==null
                || !saved.equals(level.getBlockEntity(pos).saveWithFullMetadata()))) { falling.discard(); return null; }
        if(blockEntity!=null) level.removeBlockEntity(pos);
        if(!level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS)) {
            if(saved!=null) { BlockEntity restored=BlockEntity.loadStatic(pos,state,saved); if(restored!=null) level.setBlockEntity(restored); }
            falling.discard(); return null;
        }
        return falling;
    }
    @Override public void tick() {
        if(level.isClientSide) { super.tick(); return; }
        ServerLevel server=(ServerLevel)level;
        if(!server.hasChunkAt(blockPosition())) return;
        if(time==1) for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos neighbor=blockPosition().relative(direction);
            if(server.hasChunkAt(neighbor) && server.getBlockState(neighbor).is(net.minecraft.tags.BlockTags.LEAVES)) drop(server,neighbor);
        }
        ++time; setDeltaMovement(getDeltaMovement().add(0,-.04,0));
        // Avoid crossing into an unloaded neighboring chunk even with external horizontal motion.
        BlockPos next=new BlockPos(position().add(getDeltaMovement())); if(!server.hasChunkAt(next)) return;
        move(MoverType.SELF,getDeltaMovement()); setDeltaMovement(getDeltaMovement().scale(.98));
        if(onGround) {
            BlockPos pos=blockPosition(); BlockState state=getBlockState();
            if(server.getBlockState(pos).getMaterial().isReplaceable() && state.canSurvive(server,pos)
                    && server.setBlock(pos,state,DecayPropagation.CONVERSION_FLAGS)) {
                if(originalBlockEntity!=null) {
                    BlockEntity restored=server.getBlockEntity(pos);
                    if(restored!=null) { CompoundTag tag=originalBlockEntity.copy(); tag.putInt("x",pos.getX());tag.putInt("y",pos.getY());tag.putInt("z",pos.getZ());restored.load(tag);restored.setChanged(); }
                }
                discard(); return;
            }
            fallback(server);
        } else if(getY()<server.getMinBuildHeight()-10 || time>600) fallback(server);
    }
    private void fallback(ServerLevel server) {
        List<ItemEntity> spawned=new ArrayList<>();
        for(ItemStack stack:fallback) {
            ItemEntity item=new ItemEntity(server,getX(),Math.max(server.getMinBuildHeight(),getY()),getZ(),stack.copy());
            item.setNeverPickUp(); spawned.add(item);
            if(!server.addFreshEntity(item)) { spawned.forEach(Entity::discard); return; }
        }
        spawned.forEach(ItemEntity::setDefaultPickUpDelay); discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(originalBlockEntity!=null) tag.put("ClassicBlockEntity",originalBlockEntity.copy());
        ListTag drops=new ListTag(); fallback.forEach(stack->drops.add(stack.save(new CompoundTag())));tag.put("ClassicDrops",drops);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); dropItem=false;
        originalBlockEntity=tag.contains("ClassicBlockEntity",10)?tag.getCompound("ClassicBlockEntity").copy():null;
        fallback=new ArrayList<>(); for(var value:tag.getList("ClassicDrops",10)) fallback.add(ItemStack.of((CompoundTag)value));
    }
    @Override public Packet<?> getAddEntityPacket() { return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this,Block.getId(getBlockState())); }
}
