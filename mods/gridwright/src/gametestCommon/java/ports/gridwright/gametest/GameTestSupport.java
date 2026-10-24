package ports.gridwright.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.grid.GridSnapshot;
import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.machine.ResonatorBlockEntity;
import ports.gridwright.registry.ModBlocks;

import java.util.UUID;
import java.util.function.Supplier;

/** Helpers shared by the Forge and Fabric GameTest runs. Both dev runtimes use official names. */
public final class GameTestSupport {

    public static final String TEMPLATE = "gridwright:empty";

    private GameTestSupport() { }

    /** A grid owner: a random UUID and a short unique name, so tests never share a grid. */
    record Owner(UUID id, String name) {
        static Owner random(String prefix) {
            UUID id = UUID.randomUUID();
            return new Owner(id, prefix + id.toString().substring(0, 6));
        }

        String gridKey() {
            return "player:" + id;
        }
    }

    static TestPlatform platform() {
        return TestPlatform.Holder.get();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static GridManager grid(GameTestHelper helper) {
        return GridManager.get(helper.getLevel().getServer());
    }

    static GridSnapshot snapshot(GameTestHelper helper, String key) {
        return grid(helper).snapshot(key);
    }

    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> T place(GameTestHelper helper, Supplier<? extends Block> block, BlockPos pos) {
        helper.setBlock(pos, block.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity != null, "No block entity at " + pos);
        return (T) blockEntity;
    }

    static <T extends GridBlockEntity> T owned(T member, Owner owner) {
        member.setOwner(owner.id(), owner.name());
        return member;
    }

    static ProducerBlockEntity producer(GameTestHelper helper, Supplier<? extends Block> block, BlockPos pos, Owner owner) {
        return owned(place(helper, block, pos), owner);
    }

    static ResonatorBlockEntity resonator(GameTestHelper helper, BlockPos pos, Owner owner) {
        ResonatorBlockEntity resonator = place(helper, ModBlocks.RESONATOR, pos);
        return owner == null ? resonator : owned(resonator, owner);
    }

    static GeneratorBlockEntity generator(GameTestHelper helper, GeneratorKind kind, BlockPos pos) {
        return place(helper, ModBlocks.GENERATORS.get(kind), pos);
    }

    /**
     * A sealed column of flowing water beside {@code mill}: a water source on top of the column, glass
     * all round so nothing spills out of the arena, and stone underneath. The column itself is falling
     * (never source) water.
     */
    static void waterColumn(GameTestHelper helper, BlockPos mill, Direction side) {
        BlockPos column = mill.relative(side);
        BlockPos source = column.above();
        helper.setBlock(column.below(), Blocks.STONE);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(source.relative(direction), Blocks.GLASS);
            BlockPos beside = column.relative(direction);
            if (!beside.equals(mill)) helper.setBlock(beside, Blocks.GLASS);
        }
        helper.setBlock(source.above(), Blocks.GLASS);
        helper.setBlock(source, Blocks.WATER);
    }

    static void setField(Class<?> owner, String name, Object target, Object value) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot write " + owner.getSimpleName() + "." + name, e);
        }
    }

    /** A server player who is never added to the level, for placement hooks. */
    static ServerPlayer player(GameTestHelper helper, Owner owner) {
        ServerLevel level = helper.getLevel();
        return new ServerPlayer(level.getServer(), level, new GameProfile(owner.id(), owner.name()), null);
    }
}
