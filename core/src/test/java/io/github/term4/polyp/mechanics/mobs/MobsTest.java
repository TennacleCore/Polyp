package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.mechanics.attack.AttackSystem;
import io.github.term4.polyp.mechanics.attack.HitDetection;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.types.mob.MobDamage;
import io.github.term4.polyp.mechanics.mobs.kinds.EnderDragonEntity;
import io.github.term4.polyp.mechanics.mobs.kinds.IronGolemEntity;
import io.github.term4.polyp.mechanics.mobs.kinds.SilverfishEntity;
import io.github.term4.polyp.mechanics.mobs.ai.MeleeAttackGoal;
import io.github.term4.polyp.mechanics.mobs.path.Path;
import io.github.term4.polyp.mechanics.mobs.path.Pathing;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.InstanceContainer;
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
import io.github.term4.polyp.mechanics.mobs.MobKindConfig;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import io.github.term4.polyp.api.event.mobs.MobBreakBlockEvent;
import net.minestom.server.event.EventListener;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mobs on the flat floor: a hunt, a golem's lift, the dragon's parts, the difficulty, and the two eras' paths and swings. */
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

    /** A game may keep a block from a dragon: the event's cancel leaves it, and the flight treats it as bedrock. */
    @Test
    void aKeptBlockStopsTheDragon() {
        Pos at = new Pos(100.5, 66, Z + 0.5);
        instance.setBlock(100, 67, Z, Block.STONE); // inside the body's box
        var keep = EventListener.of(MobBreakBlockEvent.class, e -> {
            if (e.position().blockX() == 100 && e.position().blockY() == 67 && e.position().blockZ() == Z) e.setCancelled(true);
        });
        MinecraftServer.getGlobalEventHandler().addListener(keep);
        EnderDragonEntity dragon = EnderDragonEntity.spawn(mobs, world, at);
        try {
            awaitSpawn(dragon);
            dragon.roamAround(at);
            tick(dragon, 2);
            assertEquals(Block.STONE, instance.getBlock(100, 67, Z), "kept");
            assertTrue(dragon.slowed(), "and it slows the flight, as bedrock does");
            MinecraftServer.getGlobalEventHandler().removeListener(keep);
            tick(dragon, 2);
            assertEquals(Block.AIR, instance.getBlock(100, 67, Z), "let go, it goes");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(keep);
            dragon.remove();
        }
    }

    /** Captured (four Hypixel games): a pass pushes once, never every tick under a wing; the engine's default is vanilla's. */
    @Test
    void aChargePushesOnceWhenTheKindSays() {
        // a block beside the body's center: inside both wing zones whatever the yaw, and never at the zero the push divides by
        FakePlayer fp = FakePlayer.connect(instance, new Pos(81.5, 65, Z + 0.5), "Winged");
        try {
            assertTrue(pushesOverThreeTicks(fp, MobKindConfig.builder().build()) >= 3, "vanilla: every tick under a wing");
            assertEquals(1, pushesOverThreeTicks(fp, MobKindConfig.builder().pushOncePerCharge(true).build()), "one push a charge");
        } finally {
            fp.player.remove();
        }
    }

    /** A charge begun away from a victim may push them again; one begun over them may not. */
    @Test
    void aLeftVictimIsReArmed() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(81.5, 65, Z + 0.5), "Rearmed");
        EnderDragonEntity dragon = EnderDragonEntity.spawn(mobs, world, new Pos(80.5, 66, Z + 0.5));
        try {
            awaitSpawn(dragon);
            dragon.roamAround(new Pos(80.5, 66, Z + 0.5));
            dragon.kind(MobKindConfig.builder().pushOncePerCharge(true).build());
            fp.sent.clear();
            tick(dragon, 1);
            assertEquals(1, pushes(fp), "the first pass");
            fp.player.teleport(new Pos(81.5, 65, Z + 20.5)).join();
            dragon.forceNewTarget();
            tick(dragon, 1);
            fp.player.teleport(new Pos(81.5, 65, Z + 0.5)).join();
            fp.sent.clear();
            tick(dragon, 1);
            assertEquals(1, pushes(fp), "a charge begun 20 blocks off pushes again");
        } finally {
            dragon.remove();
            fp.player.remove();
        }
    }

    // a fresh dragon from rest, retargeting every tick as vanilla's does with its target in reach
    private static long pushesOverThreeTicks(FakePlayer fp, MobKindConfig kind) {
        EnderDragonEntity dragon = EnderDragonEntity.spawn(mobs, world, new Pos(80.5, 66, Z + 0.5));
        try {
            awaitSpawn(dragon);
            dragon.roamAround(new Pos(80.5, 66, Z + 0.5));
            dragon.kind(kind);
            fp.sent.clear();
            for (int i = 0; i < 3; i++) {
                dragon.forceNewTarget();
                tick(dragon, 1);
            }
            return pushes(fp);
        } finally {
            dragon.remove();
        }
    }

    // a wing push at a block is 4 a tick, the bite's knockback 0.4: a packet over a block a tick is the wing's
    private static long pushes(FakePlayer fp) {
        return fp.sent(EntityVelocityPacket.class).stream()
                .filter(p -> p.entityId() == fp.player.getEntityId() && Math.abs(p.velocity().x()) + Math.abs(p.velocity().z()) >= 1.0).count();
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
    void modernWalksDiagonals() {
        int z = Z;
        SilverfishEntity fish = SilverfishEntity.spawn(mobs, world, new Pos(84.5, 64, z + 0.5));
        try {
            awaitSpawn(fish);
            tick(fish, 2);
            BlockVec to = new BlockVec(90, 64, z + 6);
            Path legacy = Pathing.LEGACY.toBlock(fish.navigation(), to);
            Path modern = Pathing.MODERN.toBlock(fish.navigation(), to);
            assertNotNull(legacy);
            assertNotNull(modern);
            assertEquals(13, legacy.length(), "1.8 walks the two legs, a cell at a time");
            assertTrue(modern.length() <= 8, "26.1 cuts the corner: " + modern.length());
            assertEquals(90, modern.finalPoint().x);
        } finally {
            fish.remove();
        }
    }

    @Test
    void aSwingBehindGlassNeedsSightOnlyModern() {
        assertTrue(swingThroughPane(io.github.term4.polyp.presets.vanilla18.Mobs.config()), "1.8 swings through the pane");
        assertFalse(swingThroughPane(io.github.term4.polyp.presets.vanilla.Mobs.config()), "26.1 wants an eye line");
    }

    // a golem beside a pane column with a zombie just past it: in reach either era, in sight in neither
    private static boolean swingThroughPane(MobsConfig cfg) {
        InstanceContainer inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.MOBS, cfg).build());
        MechanicsWorld w = MechanicsWorld.of(inst);
        for (int y = 64; y <= 66; y++) inst.setBlock(2, y, 0, Block.GLASS_PANE);
        LivingEntity zombie = zombie(new Pos(2.9, 64, 0.5));
        IronGolemEntity golem = IronGolemEntity.spawn(mobs, w, new Pos(1.7, 64, 0.5), false);
        try {
            awaitSpawn(golem);
            golem.attackTarget(zombie);
            MeleeAttackGoal goal = new MeleeAttackGoal(golem, 1.0, true);
            float before = zombie.getHealth();
            for (int i = 0; i < 3 && zombie.getHealth() == before; i++) goal.updateTask();
            return zombie.getHealth() < before;
        } finally {
            golem.remove();
            zombie.remove();
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
