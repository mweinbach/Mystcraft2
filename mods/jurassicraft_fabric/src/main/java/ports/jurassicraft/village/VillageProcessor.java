package ports.jurassicraft.village;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Resolve the old templates' random machine and fossil markers at placement. */
public final class VillageProcessor extends StructureProcessor {
    public static final Codec<VillageProcessor> CODEC = Codec.unit(VillageProcessor::new);
    @Override protected StructureProcessorType<?> getType() { return VillageRegistry.PROCESSOR.get(); }
    @Override public StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot,
            StructureBlockInfo original, StructureBlockInfo transformed, StructurePlaceSettings settings) {
        if (original.nbt == null || !original.nbt.contains("JurassicMarker")) return transformed;
        String marker = original.nbt.getString("JurassicMarker");
        RandomSource random = settings.getRandom(transformed.pos);
        BlockState state;
        switch (marker) {
            case "machine" -> {
                if (random.nextInt(4) != 0) state = Blocks.AIR.defaultBlockState();
                else {
                    Block block = switch (random.nextInt(5)) {
                        case 0 -> DnaMachineRegistry.EXTRACTOR.get();
                        case 1 -> DnaMachineRegistry.SEQUENCER.get();
                        case 2 -> DnaMachineRegistry.COMBINATOR.get();
                        case 3 -> DnaMachineRegistry.SYNTHESIZER.get();
                        default -> HatcheryRegistry.block(HatcheryRegistry.Machine.EMBRYONIC);
                    };
                    Direction direction = settings.getRotation().rotate(settings.getMirror().mirror(Direction.NORTH));
                    state = block.defaultBlockState();
                    if (state.hasProperty(HorizontalDirectionalBlock.FACING)) state = state.setValue(HorizontalDirectionalBlock.FACING, direction);
                }
            }
            case "tool" -> {
                ItemStack tool = new ItemStack(random.nextBoolean() ? Items.IRON_SHOVEL : Items.IRON_PICKAXE);
                tool.setDamageValue(20 + random.nextInt(229));
                CompoundTag nbt = new CompoundTag();
                nbt.putString("id", "jurassicraft:ancient_item_holding_block");
                nbt.put("item", tool.save(new CompoundTag()));
                nbt.putFloat("itemYOffset", .5F);
                return new StructureBlockInfo(transformed.pos, VillageRegistry.ITEM_HOLDER.get().defaultBlockState(), nbt);
            }
            case "fossil", "fossil_or_ground" -> {
                if (random.nextBoolean()) {
                    Species[] species = Species.values();
                    Species chosen = species[RandomSource.create(origin.asLong() ^ 0x5ad39b1L).nextInt(species.length)];
                    state = JurassicContent.FOSSILS.get(chosen).get().defaultBlockState();
                } else if (marker.equals("fossil_or_ground")) return null;
                else state = Blocks.SAND.defaultBlockState();
            }
            default -> { return transformed; }
        }
        return new StructureBlockInfo(transformed.pos, state, null);
    }
}
