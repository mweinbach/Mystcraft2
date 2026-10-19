package ports.jurassicraft.fieldguide;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.Reg;

/** Snapshot sent only to the player inspecting a dinosaur, as in the original guide. */
public final class FieldGuideNetwork {
    public static final ResourceLocation SNAPSHOT = Reg.id("field_guide_snapshot");
    private FieldGuideNetwork() {}

    public static void inspect(ServerPlayer player, DinosaurEntity dinosaur) {
        Snapshot snapshot = Snapshot.of(dinosaur);
        Network.sendToPlayer(player, SNAPSHOT, snapshot::write);
    }

    public record Snapshot(int entityId, int energy, int maxEnergy, int water, int maxWater,
                           boolean hungry, boolean thirsty, float health, float maxHealth,
                           boolean lowHealth, boolean poisoned, boolean lowAir, int foods, long ageTicks) {
        private static final int MEAT = 1, PLANTS = 2, FISH = 4, INSECTS = 8, KRILL = 16, PLANKTON = 32;

        public static Snapshot of(DinosaurEntity dinosaur) {
            int foods = 0;
            boolean meat = dinosaur.isFood(new ItemStack(Items.BEEF));
            if (meat) foods |= MEAT;
            if (dinosaur.isFood(new ItemStack(Items.CARROT))) foods |= PLANTS;
            if (dinosaur.isFood(new ItemStack(Items.COD))) foods |= FISH;
            if (dinosaur.isFood(new ItemStack(BugRegistry.CRICKETS.get()))) foods |= INSECTS;
            if (dinosaur.isFood(new ItemStack(BugRegistry.KRILL.get()))) foods |= KRILL;
            if (dinosaur.isFood(new ItemStack(BugRegistry.PLANKTON.get()))) foods |= PLANKTON;
            return new Snapshot(dinosaur.getId(), dinosaur.energy(), dinosaur.maximumEnergy(), dinosaur.water(), dinosaur.maximumWater(),
                    dinosaur.needsFood(), dinosaur.isThirsty(), dinosaur.isCarcass() ? 0 : dinosaur.getHealth(), dinosaur.getMaxHealth(),
                    dinosaur.isCarcass() || dinosaur.getHealth() < dinosaur.getMaxHealth() / 4,
                    dinosaur.hasEffect(MobEffects.POISON), !dinosaur.isCarcass() && dinosaur.getAirSupply() < 200,
                    foods, dinosaur.totalAgeTicks());
        }
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(entityId); buffer.writeVarInt(energy); buffer.writeVarInt(maxEnergy);
            buffer.writeVarInt(water); buffer.writeVarInt(maxWater); buffer.writeBoolean(hungry); buffer.writeBoolean(thirsty);
            buffer.writeFloat(health); buffer.writeFloat(maxHealth);
            buffer.writeBoolean(lowHealth); buffer.writeBoolean(poisoned); buffer.writeBoolean(lowAir);
            buffer.writeVarInt(foods); buffer.writeVarLong(ageTicks);
        }
        public static Snapshot read(FriendlyByteBuf buffer) {
            return new Snapshot(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarLong());
        }
        public boolean acceptsMeat() { return (foods & MEAT) != 0; }
        public boolean acceptsPlants() { return (foods & PLANTS) != 0; }
        public boolean acceptsFish() { return (foods & FISH) != 0; }
        public boolean acceptsInsects() { return (foods & INSECTS) != 0; }
        public boolean acceptsKrill() { return (foods & KRILL) != 0; }
        public boolean acceptsPlankton() { return (foods & PLANKTON) != 0; }
        public long daysLived() { return Math.max(0, ageTicks) / 24000; }
        public static String growthStage(float growth) {
            int percentage = (int) (growth * 100);
            return percentage > 75 ? "adult" : percentage > 50 ? "adolescent"
                    : percentage > 25 ? "juvenile" : "infant";
        }
        public int foodPercent() { return percentage(energy, maxEnergy); }
        public int waterPercent() { return percentage(water, maxWater); }
        private static int percentage(int value, int maximum) {
            return maximum <= 0 ? 0 : (int) Math.max(0, Math.min(100, Math.round(100.0 * value / maximum)));
        }
    }
}
