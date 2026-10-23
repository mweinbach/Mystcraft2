package ports.jurassicraft.darts;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;

public final class DartGameTests {
    public DartGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "jurassicraft:empty")
    public static void gunLoadsAndFiresServerSide(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        ItemStack gun = new ItemStack(DartRegistry.GUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().setItem(4, new ItemStack(DartRegistry.TRANQUILIZER.get(), 15));
        DartRegistry.GUN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        ItemStack loaded = ItemStack.of(gun.getOrCreateTag().getCompound("LoadedDarts"));
        require(helper, loaded.is(DartRegistry.TRANQUILIZER.get()) && loaded.getCount() == 12
                && player.getInventory().getItem(4).getCount() == 3, "gun must load at most twelve darts");
        DartRegistry.GUN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        require(helper, ItemStack.of(gun.getTag().getCompound("LoadedDarts")).getCount() == 11,
                "firing must consume exactly one dart");
        var projectiles = helper.getLevel().getEntitiesOfClass(DartProjectile.class,
                new AABB(player.blockPosition()).inflate(4), projectile -> projectile.getOwner() == player);
        require(helper, projectiles.size() == 1 && projectiles.get(0).getItem().is(DartRegistry.TRANQUILIZER.get()),
                "firing must spawn a projectile carrying the selected dart");
        projectiles.forEach(DartProjectile::discard);
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void dartsApplyTheirEffectsToDinosaurs(GameTestHelper helper) {
        var dinosaur = helper.spawnWithNoFreeWill(DinosaurRegistry.type(Species.VELOCIRAPTOR), new BlockPos(3, 1, 3));
        var player = helper.makeMockPlayer();
        var tranquilizer = new DartProjectile(helper.getLevel(), player, new ItemStack(DartRegistry.TRANQUILIZER.get()));
        tranquilizer.onHitEntity(new EntityHitResult(dinosaur));
        require(helper, dinosaur.isTranquilized() && dinosaur.tranquilizerTicks() >= 2000,
                "tranquilizer dart must put a dinosaur to sleep");

        ItemStack potion = new ItemStack(DartRegistry.POTION.get());
        PotionUtils.setCustomEffects(potion, List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1)));
        var tipped = new DartProjectile(helper.getLevel(), player, potion);
        tipped.onHitEntity(new EntityHitResult(dinosaur));
        var effect = dinosaur.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        require(helper, effect != null && effect.getDuration() == 120 && effect.getAmplifier() == 1,
                "potion dart must apply its saved effects");

        var lethal = new DartProjectile(helper.getLevel(), player, new ItemStack(DartRegistry.LETHAL.get()));
        lethal.onHitEntity(new EntityHitResult(dinosaur));
        CompoundTag saved = dinosaur.saveWithoutId(new CompoundTag());
        require(helper, dinosaur.hasEffect(MobEffects.POISON) && saved.getInt("TicksUntilDeath") == 200,
                "lethal darts must poison and schedule death");
        dinosaur.discard();
        helper.succeed();
    }
}
