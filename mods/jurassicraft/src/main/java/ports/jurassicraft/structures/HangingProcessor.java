package ports.jurassicraft.structures;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import ports.jurassicraft.mural.MuralType;

/** Translate the legacy murals' and paintings' relative anchors with the template. */
public final class HangingProcessor extends StructureProcessor {
    public static final Codec<HangingProcessor> CODEC = Codec.unit(HangingProcessor::new);
    @Override protected StructureProcessorType<?> getType() { return StructureRegistry.HANGING.get(); }
    @Override public StructureTemplate.StructureEntityInfo processEntity(LevelReader level, BlockPos origin,
            StructureTemplate.StructureEntityInfo source, StructureTemplate.StructureEntityInfo transformed,
            StructurePlaceSettings settings, StructureTemplate template) {
        CompoundTag nbt = transformed.nbt.copy();
        String id=nbt.getString("id");
        if (!id.equals("jurassicraft:mural") && !id.equals("minecraft:painting")) return transformed;
        BlockPos anchor = new BlockPos(nbt.getInt("TileX"),nbt.getInt("TileY"),nbt.getInt("TileZ"));
        anchor=StructureTemplate.calculateRelativePosition(settings,anchor).offset(origin);
        String key = id.equals("minecraft:painting") ? "facing" : "Facing";
        Direction original=Direction.from2DDataValue(nbt.getByte(key));
        // Vanilla applies entity rotation then mirror, whereas templates use mirror then rotation.
        Rotation rotation=settings.getRotation(); Mirror mirror=settings.getMirror();
        Direction desired=rotation.rotate(mirror.mirror(original));
        // Even-width murals are centered half a block to one side of their anchor.
        // Reflection reverses that offset, so preserve the reflected footprint.
        if (id.equals("jurassicraft:mural") && mirror != Mirror.NONE
                && MuralType.byId(nbt.getString("Artwork")).width % 32 == 0) {
            anchor = anchor.relative(desired.getCounterClockWise().getOpposite());
        }
        nbt.putInt("TileX",anchor.getX()); nbt.putInt("TileY",anchor.getY()); nbt.putInt("TileZ",anchor.getZ());
        Rotation inverse=switch(rotation) { case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90; case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90; default -> rotation; };
        nbt.putByte(key,(byte)inverse.rotate(mirror.mirror(desired)).get2DDataValue());
        // Vanilla calls moveTo on the returned position; HangingEntity.setPos
        // floors that position back to its anchor. Clip and move using that anchor.
        return new StructureTemplate.StructureEntityInfo(Vec3.atCenterOf(anchor),anchor,nbt);
    }
}
