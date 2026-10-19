package ports.jurassicraft.village;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaleontologistGameTests {
    private PaleontologistGameTests() {}
    private static final BlockPos STAND = new BlockPos(8, 2, 8), SAND = new BlockPos(9, 2, 8);
    private static void require(GameTestHelper h, boolean condition, String message) { if (!condition) h.fail(message); }
    private static Villager prepare(GameTestHelper h) {
        for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++)
            h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.setBlock(SAND.below(), JurassicContent.FOSSILS.get(Species.values()[0]).get());
        h.setBlock(SAND, Blocks.SAND);
        Villager villager = h.spawn(EntityType.VILLAGER, STAND);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillageRegistry.PALEONTOLOGIST.get()));
        villager.setVillagerXp(1);
        return villager;
    }
    public static final class DenyGriefing {
        private final Villager villager;
        DenyGriefing(Villager villager) { this.villager = villager; }
        @SubscribeEvent public void onGriefing(EntityMobGriefingEvent event) {
            if (event.getEntity() == villager) event.setResult(Event.Result.DENY);
        }
    }
    @GameTest(template = "park_structure_test", templateNamespace = PortMod.MOD_ID)
    public static void excavationPreservesFossilsRestoresSandAndRespectsGriefing(GameTestHelper h) {
        Villager villager = prepare(h);
        var installed = villager.goalSelector.getAvailableGoals().stream()
                .filter(entry -> entry.getGoal() instanceof PaleontologistDigGoal).findFirst();
        require(h, installed.isPresent(), "excavation task was not installed on villager join");
        PaleontologistDigGoal goal = (PaleontologistDigGoal) installed.orElseThrow().getGoal();
        DenyGriefing deny = new DenyGriefing(villager);
        MinecraftForge.EVENT_BUS.register(deny);
        try { require(h, !goal.canUse(), "task ignored the mob-griefing event"); }
        finally { MinecraftForge.EVENT_BUS.unregister(deny); }
        require(h, goal.canUse(), "task did not find nearby sand");
        goal.start();
        for (int i = 0; i < 19; i++) goal.tick();
        require(h, h.getBlockState(SAND).is(Blocks.SAND), "sand was removed before the digging delay");
        goal.tick();
        require(h, h.getBlockState(SAND).isAir(), "sand did not disappear after 20 ticks");
        for (int i = 0; i < 100; i++) goal.tick();
        require(h, h.getBlockState(SAND).isAir() && h.getBlockState(SAND.below()).getBlock() instanceof ports.jurassicraft.content.FossilBlock,
                "excavation covered or damaged the fossil");
        goal.stop();
        h.setBlock(SAND.below(), Blocks.STONE);
        h.setBlock(SAND, Blocks.SAND);
        goal = new PaleontologistDigGoal(villager);
        require(h, goal.canUse(), "task did not find restored sand");
        goal.start();
        for (int i = 0; i < 100; i++) goal.tick();
        require(h, h.getBlockState(SAND).is(Blocks.SAND), "non-fossil excavation did not restore sand");
        goal.stop();
        villager.setVillagerData(villager.getVillagerData().setProfession(VillageRegistry.GENETICIST.get()));
        require(h, !new PaleontologistDigGoal(villager).canUse(), "other professions performed excavation");
        h.succeed();
    }
    @GameTest(template = "park_structure_test", templateNamespace = PortMod.MOD_ID, timeoutTicks = 400)
    public static void excavationRunsThroughVillagerScheduler(GameTestHelper h) {
        Villager villager = prepare(h);
        var installed = villager.goalSelector.getAvailableGoals().stream()
                .filter(entry -> entry.getGoal() instanceof PaleontologistDigGoal).findFirst().orElseThrow();
        h.succeedWhen(() -> require(h, h.getBlockState(SAND).isAir(),
                "paleontologist has not excavated after " + h.getTick() + " test ticks; villager ticks=" + villager.tickCount
                        + ", position=" + villager.blockPosition() + ", profession=" + villager.getVillagerData().getProfession()
                        + ", goal running=" + installed.isRunning() + ", panic=" + villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)));
    }
}
