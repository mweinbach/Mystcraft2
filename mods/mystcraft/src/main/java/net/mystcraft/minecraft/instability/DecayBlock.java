package net.mystcraft.minecraft.instability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;

/** Explicitly placed classic hazard. No Age receives it from an invented instability score. */
public final class DecayBlock extends Block {
    private final DecayType type;
    public DecayBlock(DecayType type) {
        super(Properties.of(Material.SAND).strength(type.hardness(), type.resistance()).sound(SoundType.SAND));
        this.type = type;
    }
    public DecayType type() { return type; }
    @Override public void onPlace(BlockState state, Level level, BlockPos position, BlockState previous, boolean moving) {
        super.onPlace(state, level, position, previous, moving);
        if (level instanceof ServerLevel server) {
            // Original DecayHandler.onBlockAdded removes decay outside a Mystcraft Age.
            if (!DecayPropagation.isAge(server)) { level.removeBlock(position, false); return; }
            if (!previous.is(this)) level.scheduleTick(position, this, DecayPropagation.PULSE_INTERVAL);
        }
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        if (!DecayPropagation.isAge(level)) { level.removeBlock(position, false); return; }
        DecayPropagation.pulse(level, position, this, random);
        if (level.getBlockState(position).is(this)) level.scheduleTick(position, this, DecayPropagation.PULSE_INTERVAL);
    }
    private void contact(Level level, Entity entity) {
        if (!level.isClientSide && type == DecayType.WHITE) entity.hurt(DamageSource.MAGIC, 1);
    }
    @Override public void stepOn(Level level, BlockPos position, BlockState state, Entity entity) {
        contact(level, entity); super.stepOn(level, position, state, entity);
    }
    @Override public void entityInside(BlockState state, Level level, BlockPos position, Entity entity) { contact(level, entity); }
}
