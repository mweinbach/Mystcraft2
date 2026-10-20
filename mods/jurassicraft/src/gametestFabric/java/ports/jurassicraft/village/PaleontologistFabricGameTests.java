package ports.jurassicraft.village;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

public final class PaleontologistFabricGameTests implements FabricGameTest {
    private static final BlockPos STAND = new BlockPos(8, 2, 8), SAND = new BlockPos(9, 2, 8);
    public PaleontologistFabricGameTests() {}
    private static void require(GameTestHelper h, boolean condition, String message) { if (!condition) h.fail(message); }

    @GameTest(template = "jurassicraft:park_structure_test", timeoutTicks = 400)
    public void excavationRunsThroughVillagerScheduler(GameTestHelper h) {
        for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++)
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.setBlock(SAND.below(), JurassicContent.FOSSILS.get(Species.values()[0]).get());
        h.setBlock(SAND, Blocks.SAND);
        Villager villager = h.spawn(EntityType.VILLAGER, STAND);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillageRegistry.PALEONTOLOGIST.get()));
        villager.setVillagerXp(1);
        var installed = villager.goalSelector.getAvailableGoals().stream()
                .filter(entry -> entry.getGoal() instanceof PaleontologistDigGoal).findFirst();
        require(h, installed.isPresent(), "excavation task was not installed on villager join");
        h.succeedWhen(() -> require(h, h.getBlockState(SAND).isAir(),
                "paleontologist has not excavated after " + h.getTick() + " test ticks; villager ticks=" + villager.tickCount
                        + ", position=" + villager.blockPosition() + ", goal running=" + installed.get().isRunning()
                        + ", panic=" + villager.getBrain().isActive(Activity.PANIC)));
    }
}
