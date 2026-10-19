package ports.buildcraft.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.Reg;
import ports.buildcraft.transport.behaviour.DiamondItemBehaviour;
import ports.buildcraft.transport.behaviour.IronBehaviour;
import ports.buildcraft.transport.behaviour.PipeBehaviour;
import ports.buildcraft.transport.behaviour.SimpleBehaviours;
import ports.buildcraft.transport.behaviour.SimpleBehaviours.Separate.Kind;
import ports.buildcraft.transport.behaviour.WoodBehaviour;

/**
 * Upstream {@code BCTransportPipes} and {@code BCTransportConfig}: the ported pipes with their
 * default transfer rates ({@code baseFluidRate = 10} mB/t, {@code basePowerRate = 4} MJ/t).
 */
public final class BCTransport {
    private static final int BASE_FLOW_RATE = 10;
    private static final long BASE_POWER_RATE = 4 * Power.MJ;

    public static final List<PipeDefinition> PIPES = new ArrayList<>();
    public static final List<PipeDefinition> ITEM_PIPES = new ArrayList<>();
    public static final List<PipeDefinition> FLUID_PIPES = new ArrayList<>();
    public static final List<PipeDefinition> POWER_PIPES = new ArrayList<>();

    public static final PipeDefinition WOOD_ITEM = item("wood_item", WoodBehaviour::new).texture("wood_item_clear").special("wood_item_filled").build();
    public static final PipeDefinition COBBLESTONE_ITEM = item("cobblestone_item", separate(Kind.COBBLESTONE)).build();
    public static final PipeDefinition STONE_ITEM = item("stone_item", separate(Kind.STONE)).build();
    public static final PipeDefinition QUARTZ_ITEM = item("quartz_item", separate(Kind.QUARTZ)).build();
    public static final PipeDefinition GOLD_ITEM = item("gold_item", SimpleBehaviours.Gold::new).build();
    public static final PipeDefinition SANDSTONE_ITEM = item("sandstone_item", SimpleBehaviours.Sandstone::new).build();
    public static final PipeDefinition IRON_ITEM = item("iron_item", IronBehaviour::new).texture("iron_item_filled").special("iron_item_clear").build();
    public static final PipeDefinition DIAMOND_ITEM = item("diamond_item", DiamondItemBehaviour::new)
            .faceTextures(diamondTextures("diamond_item"), "diamond_item_itemstack").menu().build();
    public static final PipeDefinition CLAY_ITEM = item("clay_item", SimpleBehaviours.Clay::new).build();
    public static final PipeDefinition VOID_ITEM = item("void_item", SimpleBehaviours.Void::new).build();

    public static final PipeDefinition WOOD_FLUID = fluid("wood_fluid", WoodBehaviour::new, 1, 10).texture("wood_fluid_clear").special("wood_fluid_filled").build();
    public static final PipeDefinition COBBLESTONE_FLUID = fluid("cobblestone_fluid", separate(Kind.COBBLESTONE), 1, 10).build();
    public static final PipeDefinition STONE_FLUID = fluid("stone_fluid", separate(Kind.STONE), 2, 10).build();
    public static final PipeDefinition QUARTZ_FLUID = fluid("quartz_fluid", separate(Kind.QUARTZ), 4, 10).build();
    public static final PipeDefinition GOLD_FLUID = fluid("gold_fluid", SimpleBehaviours.Gold::new, 8, 2).build();
    public static final PipeDefinition SANDSTONE_FLUID = fluid("sandstone_fluid", SimpleBehaviours.Sandstone::new, 2, 10).build();
    public static final PipeDefinition IRON_FLUID = fluid("iron_fluid", IronBehaviour::new, 4, 10).texture("iron_fluid_filled").special("iron_fluid_clear").build();
    public static final PipeDefinition CLAY_FLUID = fluid("clay_fluid", SimpleBehaviours.Clay::new, 4, 10).build();
    public static final PipeDefinition VOID_FLUID = fluid("void_fluid", SimpleBehaviours.Void::new, 8, 10).build();

    public static final PipeDefinition WOOD_POWER = power("wood_power", SimpleBehaviours.WoodPower::new, 4, true).texture("wood_power_clear").special("wood_power_filled").build();
    public static final PipeDefinition COBBLESTONE_POWER = power("cobblestone_power", separate(Kind.COBBLESTONE), 1, false).build();
    public static final PipeDefinition STONE_POWER = power("stone_power", separate(Kind.STONE), 2, false).build();
    public static final PipeDefinition QUARTZ_POWER = power("quartz_power", separate(Kind.QUARTZ), 8, false).build();
    public static final PipeDefinition GOLD_POWER = power("gold_power", SimpleBehaviours.Gold::new, 32, false).build();
    public static final PipeDefinition SANDSTONE_POWER = power("sandstone_power", SimpleBehaviours.Sandstone::new, 4, false).build();

    public static final Supplier<BlockEntityType<PipeBlockEntity>> PIPE_TYPE = Reg.blockEntity("pipe",
            () -> BlockEntityType.Builder.of(PipeBlockEntity::new, PIPES.stream().map(PipeDefinition::block).toArray(Block[]::new)).build(null));

    public static final Supplier<MenuType<DiamondPipeMenu>> DIAMOND_PIPE_MENU = Platform.menu("diamond_pipe", DiamondPipeMenu::new);

    static {
        for (PipeDefinition definition : PIPES) {
            definition.block = Reg.block(definition.registryName(), () -> new PipeBlock(pipeProperties(), definition));
            definition.item = Reg.item(definition.registryName(), () -> new PipeItem(definition.block(), BuildCraft.pipeProperties(), definition));
        }
    }

    public static final Supplier<Item> WATERPROOF = Reg.item("waterproof", () -> new Item(BuildCraft.pipeProperties()));

    private BCTransport() {}

    public static void init() {}

    private static BlockBehaviour.Properties pipeProperties() {
        return BlockBehaviour.Properties.of(Material.METAL).strength(0.25F, 3.0F).sound(SoundType.STONE).noOcclusion();
    }

    private static Function<PipeBlockEntity, PipeBehaviour> separate(Kind kind) {
        return pipe -> new SimpleBehaviours.Separate(pipe, kind);
    }

    private static String[] diamondTextures(String prefix) {
        String[] textures = new String[6];
        for (Direction face : Direction.values()) textures[face.ordinal()] = prefix + "_" + face.getSerializedName();
        return textures;
    }

    private static PipeDefinition.Builder item(String name, Function<PipeBlockEntity, PipeBehaviour> behaviour) {
        return PipeDefinition.builder(name, PipeDefinition.Flow.ITEM, behaviour, PIPES, ITEM_PIPES);
    }

    private static PipeDefinition.Builder fluid(String name, Function<PipeBlockEntity, PipeBehaviour> behaviour, int rateMultiplier, int delay) {
        return PipeDefinition.builder(name, PipeDefinition.Flow.FLUID, behaviour, PIPES, FLUID_PIPES)
                .fluid(BASE_FLOW_RATE * rateMultiplier, delay);
    }

    private static PipeDefinition.Builder power(String name, Function<PipeBlockEntity, PipeBehaviour> behaviour, int rateMultiplier, boolean receiver) {
        return PipeDefinition.builder(name, PipeDefinition.Flow.POWER, behaviour, PIPES, POWER_PIPES)
                .power(BASE_POWER_RATE * rateMultiplier, receiver);
    }
}
