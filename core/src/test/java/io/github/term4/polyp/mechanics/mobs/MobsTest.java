package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.mechanics.attack.AttackSystem;
import io.github.term4.polyp.mechanics.attack.HitDetection;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.types.mob.MobDamage;
import io.github.term4.polyp.mechanics.mobs.kinds.EnderDragonEntity;
import io.github.term4.polyp.mechanics.mobs.kinds.IronGolemEntity;
import io.github.term4.polyp.mechanics.mobs.kinds.SilverfishEntity;
import io.github.term4.polyp.mechanics.mobs.path.Path;
import io.github.term4.polyp.presets.vanilla18.Attack;
import io.github.term4.polyp.presets.vanilla18.Mobs;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.8 mobs on the flat floor: a hunt, a golem's lift, the dragon's parts, the difficulty and a path. */
class MobsTest extends HeadlessServerTest {

    private static final int Z = 900;
    private static MobsSystem mobs;
    private static MechanicsWorld world;

    @BeforeAll
    static void install() {
        // an idle 1.8 silverfish crawls into the stone floor one time in sixty: the hunt test needs it above ground
        MobsConfig cfg = Mobs.config();
        mobs = MobsSystem.install(polyp, cfg.toBuilder()
                .kind(EntityType.SILVERFISH, Mobs.silverfish().toBuilder().hidesInBlocks(false).build())
                .build());
        AttackSystem.install(polyp, Attack.config(), HitDetection.PACKET);
        world = MechanicsWorld.of(instance);
        for (int cx = -1; cx <= 6; cx++) instance.loadChunk(cx, Z >> 4).join();
    }

    private static void tick(Entity e, int times) {
        for (int i = 0; i < times; i++) e.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    @Test
    void aSilverfishHuntsAndBites() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(8.5, 64, Z + 0.5), "Bitten");
        SilverfishEntity fish = SilverfishEntity.spawn(mobs, world, new Pos(2.5, 64, Z + 0.5));
        try {
            awaitSpawn(fish);
            float before = fp.player.getHealth();
            double start = fish.getPosition().distance(fp.player.getPosition());
            for (int i = 0; i < 600 && fp.player.getHealth() == before; i++) tick(fish, 1);
            assertTrue(fish.getPosition().distance(fp.player.getPosition()) < start, "walked toward the player");
            assertEquals(before - 1.5f, fp.player.getHealth(), 0.01f, "1 through easy: half plus one");
        } finally {
            fish.remove();
            fp.player.remove();
        }
    }

    @Test
    void theGolemLiftsItsVictim() {
        LivingEntity zombie = zombie(new Pos(20.5, 64, Z + 0.5));
        IronGolemEntity golem = IronGolemEntity.spawn(mobs, world, new Pos(22.5, 64, Z + 0.5), false);
        try {
            awaitSpawn(golem);
            assertTrue(golem.attack(zombie), "the swing landed");
            float dealt = 20 - zombie.getHealth();
            assertTrue(dealt >= 7 && dealt <= 21, "7 + rand(15), unscaled on a mob: " + dealt);
            double up = zombie.getVelocity().y() / ServerFlag.SERVER_TICKS_PER_SECOND;
            assertTrue(up >= 0.75, "0.4 knockback plus the 0.4 lift: " + up);
        } finally {
            golem.remove();
            zombie.remove();
        }
    }

    @Test
    void aBodyHitIsAQuarterPlusOne() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(40.5, 64, Z + 0.5), "Slayer");
        EnderDragonEntity first = EnderDragonEntity.spawn(mobs, world, new Pos(40.5, 66, Z + 0.5));
        EnderDragonEntity second = EnderDragonEntity.spawn(mobs, world, new Pos(40.5, 66, Z + 0.5));
        try {
            awaitSpawn(first);
            awaitSpawn(second);
            swing(fp, first.getEntityId() + 1);
            assertEquals(199.0f, first.getHealth(), 0.01f, "a fist on the head lands whole");
            swing(fp, second.getEntityId() + 2);
            assertEquals(200.0f - 1.25f, second.getHealth(), 0.01f, "a fist on the body: a quarter plus one");
            swing(fp, second.getEntityId());
            assertEquals(200.0f - 1.25f, second.getHealth(), 0.01f, "the dragon itself is not a target");
        } finally {
            first.remove();
            second.remove();
            fp.player.remove();
        }
    }

    private static void swing(FakePlayer fp, int targetId) {
        EventDispatcher.call(new PlayerPacketEvent(fp.player, new ClientAttackPacket(targetId)));
    }

    @Test
    void difficultyScalesOnPlayersOnly() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(60.5, 64, Z + 0.5), "Scaled");
        LivingEntity zombie = zombie(new Pos(62.5, 64, Z + 0.5));
        SilverfishEntity fish = SilverfishEntity.spawn(mobs, world, new Pos(61.5, 64, Z + 0.5));
        try {
            awaitSpawn(fish);
            DamageSnapshot onPlayer = MobDamage.INSTANCE.snapshot(fish, fp.player, 10f, false, services);
            assertEquals(6f, onPlayer.amount(), 0.001f, "easy: 10 / 2 + 1");
            DamageSnapshot onMob = MobDamage.INSTANCE.snapshot(fish, zombie, 10f, false, services);
            assertEquals(10f, onMob.amount(), 0.001f);
        } finally {
            fish.remove();
            zombie.remove();
            fp.player.remove();
        }
    }

    @Test
    void aPathClimbsOneBlockNotTwo() {
        int z = Z + 8;
        instance.setBlock(new Vec(72, 64, z), Block.STONE);
        instance.setBlock(new Vec(76, 64, z), Block.STONE);
        instance.setBlock(new Vec(76, 65, z), Block.STONE);
        SilverfishEntity fish = SilverfishEntity.spawn(mobs, world, new Pos(70.5, 64, z + 0.5));
        try {
            awaitSpawn(fish);
            tick(fish, 2);
            Path step = fish.navigation().pathTo(72.5, 65, z + 0.5);
            assertNotNull(step, "a one-block step is walkable");
            assertEquals(65, step.finalPoint().y, "the path ends on the step");
            Path pillar = fish.navigation().pathTo(76.5, 66, z + 0.5);
            assertTrue(pillar == null || pillar.finalPoint().y != 66, "a two-block pillar is not");
        } finally {
            fish.remove();
        }
    }

    @Test
    void anUnknownKindStillSpawns() {
        MobsConfig cfg = Mobs.config();
        assertNotNull(cfg.kind(EntityType.SILVERFISH));
        assertNull(cfg.kinds.get(EntityType.ZOMBIE.key()));
        assertNotNull(cfg.kind(EntityType.ZOMBIE), "the defaults serve any type");
    }
}
