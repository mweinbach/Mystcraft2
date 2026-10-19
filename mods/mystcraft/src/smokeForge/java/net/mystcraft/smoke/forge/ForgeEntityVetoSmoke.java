package net.mystcraft.smoke.forge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.mystcraft.minecraft.linking.LinkingService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Real Forge dimension-travel and destination-spawn vetoes must preserve generic travelers. */
public final class ForgeEntityVetoSmoke {
    private static final BlockPos SOURCE = new BlockPos(112, 200, 112), ARRIVAL = new BlockPos(128, 200, 128);
    private ForgeEntityVetoSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel origin) {
        ServerLevel target = server.getLevel(origin.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
        check(target != null, "Forge entity veto requires a real destination dimension");
        Map<ServerLevel, Map<BlockPos, BlockState>> saved = new LinkedHashMap<>();
        List<Entity> created = new ArrayList<>();
        String marker = UUID.randomUUID().toString();
        Listener veto = new Listener(target);
        boolean registered = false;
        try {
            pad(origin, SOURCE, saved); pad(target, ARRIVAL, saved);
            target.setBlock(ARRIVAL.below(), Blocks.AIR.defaultBlockState(), 3);
            target.setBlock(ARRIVAL.below(2), Blocks.AIR.defaultBlockState(), 3);
            ItemStack book = new ItemStack(MystcraftItems.LINKING_BOOK.get());
            new LinkDestination(target.dimension().location(), 128.5, 200, 128.5, 90, 10).write(book);
            CompoundTag flags = new CompoundTag(); flags.putBoolean(LinkModifiers.GENERATE_PLATFORM, true);
            book.getOrCreateTag().put("Flags", flags);
            ItemStack gems = new ItemStack(Items.EMERALD, 9); gems.getOrCreateTag().putString("SmokeEntityVeto", marker);
            ItemEntity item = new ItemEntity(origin, 112.5, 200, 112.5, gems); created.add(item);
            check(origin.addFreshEntity(item), "veto item must spawn in source");
            item.setDeltaMovement(.1, .2, .3);
            veto.source = item.getUUID(); veto.travel = true;
            MinecraftForge.EVENT_BUS.register(veto); registered = true;
            check(LinkingService.linkFromPortal((Entity) item, book) == null && veto.travelEvents == 1,
                    "item transfer must honor exactly one real Forge travel veto");
            unchanged(origin, target, item, new Vec3(.1, .2, .3));
            check(ItemStack.matches(item.getItem(), gems), "veto must preserve item stack count and NBT");

            Mob pig = (Mob) EntityType.PIG.create(origin); check(pig != null, "veto mob must construct"); created.add(pig);
            pig.setNoAi(true); pig.setPos(112.5, 200, 112.5);
            ItemStack diamonds = new ItemStack(Items.DIAMOND, 3); diamonds.getOrCreateTag().putString("SmokeEntityVeto", marker);
            pig.setItemSlot(EquipmentSlot.MAINHAND, diamonds); pig.setDropChance(EquipmentSlot.MAINHAND, 2);
            check(origin.addFreshEntity(pig), "veto mob must spawn in source"); pig.setDeltaMovement(.1, .2, .3);
            flags.putBoolean(LinkModifiers.DISARM, true);
            veto.source = pig.getUUID(); veto.travelEvents = 0;
            check(LinkingService.linkFromPortal((Entity) pig, book) == null && veto.travelEvents == 1,
                    "mob Disarm transfer must honor exactly one real Forge travel veto");
            unchanged(origin, target, pig, new Vec3(.1, .2, .3));
            check(pig.getItemBySlot(EquipmentSlot.MAINHAND) == diamonds, "travel veto must preserve original mob equipment identity");
            noDrops(origin, target, marker);

            veto.travel = false; veto.spawn = true; veto.travelEvents = 0;
            check(LinkingService.linkFromPortal((Entity) pig, book) == null
                    && veto.travelEvents == 1 && veto.spawnEvents == 1,
                    "destination insertion veto must reject after one travel event and one actual spawn event");
            unchanged(origin, target, pig, new Vec3(.1, .2, .3));
            check(pig.getItemBySlot(EquipmentSlot.MAINHAND) == diamonds,
                    "destination spawn veto must not delete original mob or its inventory");
            noDrops(origin, target, marker);
            return List.of("Forge entity veto: real item travel event preserves source entity/stack/motion",
                    "Forge entity veto: real mob travel event rolls back Disarm drops and preserves portal platform suppression",
                    "Forge entity veto: destination spawn cancellation preserves original mob and equipment without duplicates");
        } finally {
            if (registered) MinecraftForge.EVENT_BUS.unregister(veto);
            for (Entity entity : created) entity.discard();
            for (ItemEntity drop : origin.getEntitiesOfClass(ItemEntity.class, new AABB(SOURCE).inflate(6), entity ->
                    entity.getItem().hasTag() && marker.equals(entity.getItem().getTag().getString("SmokeEntityVeto")))) drop.discard();
            for (var level : saved.entrySet()) for (var block : level.getValue().entrySet()) level.getKey().setBlock(block.getKey(), block.getValue(), 3);
        }
    }
    private static void unchanged(ServerLevel origin, ServerLevel target, Entity entity, Vec3 motion) {
        check(!entity.isRemoved() && entity.level == origin && origin.getEntity(entity.getUUID()) == entity
                && target.getEntity(entity.getUUID()) == null && entity.position().distanceToSqr(new Vec3(112.5, 200, 112.5)) < 1.0E-8
                && entity.getDeltaMovement().distanceToSqr(motion) < 1.0E-8, "veto must preserve original source instance/position/motion with no target duplicate");
        check(target.getBlockState(ARRIVAL.below()).isAir() && target.getBlockState(ARRIVAL.below(2)).isAir(),
                "external portal must leave arrival platform suppressed even on veto");
    }
    private static void noDrops(ServerLevel origin, ServerLevel target, String marker) {
        check(origin.getEntitiesOfClass(ItemEntity.class, new AABB(SOURCE).inflate(6), entity ->
                entity.isAlive() && entity.getItem().is(Items.DIAMOND) && entity.getItem().hasTag()
                        && marker.equals(entity.getItem().getTag().getString("SmokeEntityVeto"))).isEmpty(), "veto must remove provisional Disarm equipment drops");
    }
    private static void pad(ServerLevel level, BlockPos center, Map<ServerLevel, Map<BlockPos, BlockState>> saved) {
        Map<BlockPos, BlockState> states = saved.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 3, 2))) {
            check(level.getBlockEntity(pos) == null, "entity veto cannot overwrite existing block entities");
            states.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, pos.getY() == center.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static final class Listener {
        private final ServerLevel destination;
        private UUID source;
        private boolean travel, spawn;
        private int travelEvents, spawnEvents;
        Listener(ServerLevel destination) { this.destination = destination; }
        @SubscribeEvent public void travel(EntityTravelToDimensionEvent event) {
            if (event.getEntity().getUUID().equals(source)) { travelEvents++; if (travel) event.setCanceled(true); }
        }
        @SubscribeEvent public void spawn(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)
                    && item.getItem().hasTag() && item.getItem().getTag().contains("SmokeEntityVeto")) {
                check(item.saveWithoutId(new CompoundTag()).getShort("PickupDelay") == 32767,
                        "provisional equipment drops must be nonmergeable and nonpickupable inside actual join hook");
            }
            if (spawn && event.getLevel() == destination && event.getEntity().getUUID().equals(source)) {
                spawnEvents++; event.setCanceled(true);
            }
        }
    }
}
