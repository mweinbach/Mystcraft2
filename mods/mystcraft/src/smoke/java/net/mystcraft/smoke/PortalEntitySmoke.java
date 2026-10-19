package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.mystcraft.minecraft.portal.BookReceptacleBlock;
import net.mystcraft.minecraft.portal.BookReceptacleBlockEntity;

/** Actual crystal-field collisions for item and mob travelers, including Disarm semantics. */
public final class PortalEntitySmoke {
    private static final BlockPos SOURCE = new BlockPos(352, 200, 352), ARRIVAL = new BlockPos(368, 200, 368);
    private PortalEntitySmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel origin) {
        ServerLevel target = server.getLevel(origin.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
        require(target != null, "entity portal smoke needs a real second dimension");
        Map<ServerLevel, Map<BlockPos, BlockState>> snapshots = new LinkedHashMap<>();
        List<Entity> entities = new ArrayList<>();
        String marker = "mystcraft-smoke-portal-" + UUID.randomUUID();
        BookReceptacleBlockEntity source = null;
        List<String> passed = new ArrayList<>();
        try {
            for (BlockPos pos : BlockPos.betweenClosed(ARRIVAL.offset(-2, -1, -2), ARRIVAL.offset(2, 3, 2))) {
                remember(target, pos, snapshots);
                target.setBlock(pos, pos.getY() == ARRIVAL.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) {
                BlockPos pos = SOURCE.offset(x, y, 0); remember(origin, pos, snapshots);
                origin.setBlock(pos, x == 0 || x == 4 || y == 0 || y == 4
                        ? PortalRegistry.CRYSTAL.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            BlockPos receptor = SOURCE.north(); remember(origin, receptor, snapshots);
            origin.setBlock(receptor, PortalRegistry.BOOK_RECEPTACLE.get().defaultBlockState().setValue(BookReceptacleBlock.FACING, Direction.NORTH), 3);
            source = (BookReceptacleBlockEntity) origin.getBlockEntity(receptor);
            require(source != null, "portal source block entity must exist");
            ItemStack book = new ItemStack(MystcraftItems.LINKING_BOOK.get());
            new LinkDestination(target.dimension().location(), 368.5, 200, 368.5, 90, 15).write(book);
            CompoundTag portalFlags = new CompoundTag(); portalFlags.putBoolean(LinkModifiers.GENERATE_PLATFORM, true); book.getOrCreateTag().put("Flags", portalFlags);
            remember(target, ARRIVAL.below(2), snapshots);
            target.setBlock(ARRIVAL.below(), Blocks.AIR.defaultBlockState(), 3);
            target.setBlock(ARRIVAL.below(2), Blocks.AIR.defaultBlockState(), 3);
            source.setBook(book);
            ItemStack retained = source.getBook().copy();
            BlockPos field = SOURCE.offset(1, 1, 0);
            require(origin.getBlockState(field).is(PortalRegistry.LINK_PORTAL.get()), "real crystal frame must activate before entity collision");

            ItemStack gems = new ItemStack(Items.EMERALD, 7); gems.getOrCreateTag().putString("SmokePortal", marker);
            ItemEntity item = new ItemEntity(origin, 353.5, 201, 352.5, gems); item.addTag(marker); entities.add(item);
            item.setDeltaMovement(0, .4, 1); require(origin.addFreshEntity(item), "source item entity must spawn");
            UUID itemId = item.getUUID(); collide(origin, field, item);
            Entity transported = target.getEntity(itemId); if (transported != null) entities.add(transported);
            require(item.isRemoved() && transported instanceof ItemEntity
                    && ItemStack.matches(((ItemEntity) transported).getItem(), gems), "actual portal collision must replace item in target while preserving exact stack NBT/count");
            require(transported.position().distanceToSqr(new Vec3(368.5, 200, 368.5)) < 1.0E-8
                    && transported.getDeltaMovement().distanceToSqr(new Vec3(-1, .6, 0)) < 1.0E-8
                    && transported.isOnPortalCooldown(), "item portal transfer must preserve redirected momentum and portal cooldown");
            require(ItemStack.matches(source.getBook(), retained), "item traveler must not consume or modify source book");
            require(target.getBlockState(ARRIVAL.below()).isAir(), "nonplayer portal must suppress authored Generate Platform");
            transported.discard();
            passed.add("entity portals: real item collision transfers exact stack/NBT to target with classic redirected motion and retained source book");

            Mob pig = (Mob) EntityType.PIG.create(origin); require(pig != null, "vanilla pig must construct");
            pig.setNoAi(true); pig.setPersistenceRequired(); pig.addTag(marker); entities.add(pig);
            pig.setCustomName(Component.literal("Smoke portal pig")); pig.setPos(353.5, 201, 352.5);
            ItemStack equipment = new ItemStack(Items.GOLDEN_SWORD); equipment.getOrCreateTag().putString("SmokePortal", marker);
            // Vanilla Mob.removeAfterChangingDimensions zeroes every original equipment stack.
            // Compare with an independent pre-transfer snapshot, never that invalidated alias.
            ItemStack expectedEquipment = equipment.copy();
            pig.setItemSlot(EquipmentSlot.MAINHAND, equipment); pig.setDropChance(EquipmentSlot.MAINHAND, 2);
            pig.setDeltaMovement(0, .4, 1); require(origin.addFreshEntity(pig), "source mob must spawn");
            UUID pigId = pig.getUUID(); collide(origin, field, pig);
            Entity movedPig = target.getEntity(pigId); if (movedPig != null) entities.add(movedPig);
            require(pig.isRemoved() && movedPig instanceof Mob && movedPig.getType() == EntityType.PIG
                    && movedPig.getCustomName() != null && movedPig.getCustomName().getString().equals("Smoke portal pig")
                    && ItemStack.matches(((Mob) movedPig).getItemBySlot(EquipmentSlot.MAINHAND), expectedEquipment),
                    "mob portal transfer must preserve type, UUID, custom name and equipment NBT; source=" + describe(pig)
                    + "; targetByUuid=" + describe(movedPig) + "; expectedEquipment=" + expectedEquipment.save(new CompoundTag())
                    + "; modifiers=" + LinkModifiers.from(source.getBook())
                    + "; taggedTargetEntities=" + target.getEntitiesOfClass(Entity.class, new AABB(ARRIVAL).inflate(8),
                            entity -> entity.getTags().contains(marker)).stream().map(PortalEntitySmoke::describe).toList());
            require(ItemStack.matches(source.getBook(), retained), "mob traveler must retain source book");
            movedPig.discard();
            passed.add("entity portals: real mob collision preserves UUID/type/name/equipment across dimensions without vanilla portals");

            CompoundTag flags = new CompoundTag(); flags.putBoolean(LinkModifiers.DISARM, true);
            source.getBook().getOrCreateTag().put("Flags", flags);
            ItemEntity rejected = new ItemEntity(origin, 353.5, 201, 352.5, gems.copy());
            rejected.addTag(marker); entities.add(rejected); require(origin.addFreshEntity(rejected), "Disarm item fixture must spawn");
            collide(origin, field, rejected);
            require(!rejected.isRemoved() && rejected.level == origin && target.getEntity(rejected.getUUID()) == null
                    && ItemStack.matches(rejected.getItem(), gems), "Disarm source must reject item entities without consumption or duplication");
            rejected.discard();
            passed.add("entity portals: Disarm rejects item travelers unchanged, matching classic permission rules");

            Mob armed = (Mob) EntityType.PIG.create(origin); require(armed != null, "armed pig fixture must construct");
            armed.setNoAi(true); armed.addTag(marker); armed.setPos(353.5, 201, 352.5); entities.add(armed);
            ItemStack diamonds = new ItemStack(Items.DIAMOND, 3); diamonds.getOrCreateTag().putString("SmokePortal", marker);
            armed.setItemSlot(EquipmentSlot.MAINHAND, diamonds); armed.setDropChance(EquipmentSlot.MAINHAND, 2);
            require(origin.addFreshEntity(armed), "armed mob must spawn"); UUID armedId = armed.getUUID(); collide(origin, field, armed);
            Entity disarmed = target.getEntity(armedId); if (disarmed != null) entities.add(disarmed);
            require(disarmed instanceof Mob && ((Mob) disarmed).getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                    "Disarm must clear transferred mob equipment only after successful travel");
            List<ItemEntity> ejected = origin.getEntitiesOfClass(ItemEntity.class, new AABB(SOURCE).inflate(7), drop ->
                    drop.getItem().is(Items.DIAMOND) && drop.getItem().hasTag()
                            && marker.equals(drop.getItem().getTag().getString("SmokePortal")));
            entities.addAll(ejected);
            require(ejected.stream().mapToInt(drop -> drop.getItem().getCount()).sum() == 3,
                    "Disarm must leave guaranteed-drop mob equipment exactly once in source world");
            passed.add("entity portals: Disarm ejects guaranteed mob equipment once at source and clears destination equipment");
            return List.copyOf(passed);
        } finally {
            for (Entity entity : entities) entity.discard();
            for (ItemEntity drop : origin.getEntitiesOfClass(ItemEntity.class, new AABB(SOURCE).inflate(7), item ->
                    item.getItem().hasTag() && marker.equals(item.getItem().getTag().getString("SmokePortal")))) drop.discard();
            if (source != null) source.takeBook();
            for (var level : snapshots.entrySet()) for (var block : level.getValue().entrySet()) level.getKey().setBlock(block.getKey(), block.getValue(), 3);
        }
    }

    private static void collide(ServerLevel level, BlockPos pos, Entity entity) {
        BlockState state = level.getBlockState(pos); state.getBlock().entityInside(state, level, pos, entity);
    }
    private static void remember(ServerLevel level, BlockPos pos, Map<ServerLevel, Map<BlockPos, BlockState>> snapshots) {
        require(level.getBlockEntity(pos) == null, "entity portal smoke cannot overwrite existing block entities");
        snapshots.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String describe(Entity entity) {
        if (entity == null) return "null";
        return "{uuid=" + entity.getUUID() + ",type=" + EntityType.getKey(entity.getType())
                + ",removed=" + entity.isRemoved() + ",alive=" + entity.isAlive() + ",level=" + entity.level.dimension().location()
                + ",position=" + entity.position() + ",name=" + entity.getCustomName() + ",cooldown=" + entity.isOnPortalCooldown()
                + (entity instanceof Mob mob ? ",equipment=" + mob.getItemBySlot(EquipmentSlot.MAINHAND).save(new CompoundTag()) : "") + "}";
    }
}
