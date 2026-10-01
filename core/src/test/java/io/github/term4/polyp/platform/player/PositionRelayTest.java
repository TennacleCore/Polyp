package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityPositionAndRotationPacket;
import net.minestom.server.network.packet.server.play.EntityPositionPacket;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.8 tracker's gates ({@code EntityTrackerEntry.track}) on what a watcher sees of a player's moves. */
class PositionRelayTest extends HeadlessServerTest {

    private record Pair(FakePlayer mover, FakePlayer watcher) {}

    private static void with(PlayerConfig.PositionRelay relay, int interval, String tag, Consumer<Pair> body) {
        PlayerConfig cfg = PlayerConfig.builder().positionBroadcastInterval(interval).positionRelay(relay).build();
        Instance inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.PLAYER, cfg).build());
        FakePlayer mover = FakePlayer.connect(inst, new Pos(0.5, 64, 0.5), "Mv" + tag);
        FakePlayer watcher = FakePlayer.connect(inst, new Pos(4.5, 64, 0.5), "Wt" + tag);
        try {
            mover.player.updateViewableRule(_ -> true); // batched viewer packets only reach socket connections
            PlayerConfigApplier.apply(polyp, mover.player);
            throughBeat(mover);
            watcher.sent.clear();
            body.accept(new Pair(mover, watcher));
        } finally {
            mover.player.remove();
            watcher.player.remove();
        }
    }

    private static void tick(FakePlayer p) {
        p.player.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    private static void throughBeat(FakePlayer p) {
        do tick(p); while (p.player.getAliveTicks() % 2 != 0);
    }

    private static void move(FakePlayer p, double dx, double dy, double dz) {
        p.player.refreshPosition(p.player.getPosition().add(dx, dy, dz), false, true);
    }

    private static List<ServerPacket> moves(FakePlayer watcher, FakePlayer mover) {
        int id = mover.player.getEntityId();
        return watcher.sent.stream().map(p -> SendablePacket.extractServerPacket(ConnectionState.PLAY, p))
                .filter(p -> p instanceof EntityPositionAndRotationPacket r && r.entityId() == id
                        || p instanceof EntityPositionPacket m && m.entityId() == id
                        || p instanceof EntityPositionSyncPacket s && s.entityId() == id
                        || p instanceof EntityTeleportPacket t && t.entityId() == id
                        || p instanceof EntityRotationPacket l && l.entityId() == id).toList();
    }

    @Test
    void aSmallMoveWaitsForAnEighth() {
        with(PlayerConfig.PositionRelay.LEGACY, 2, "Eighth", pair -> {
            move(pair.mover(), 0.05, 0, 0);
            throughBeat(pair.mover());
            assertTrue(moves(pair.watcher(), pair.mover()).isEmpty(), "a twentieth of a block waits");
            move(pair.mover(), 0.1, 0, 0);
            throughBeat(pair.mover());
            List<ServerPacket> sent = moves(pair.watcher(), pair.mover());
            assertEquals(1, sent.size(), "past an eighth since the last one sent");
            assertInstanceOf(EntityPositionPacket.class, sent.getFirst(), "the move alone");
        });
    }

    @Test
    void aLeftoverGoesOnTheSixtieth() {
        with(PlayerConfig.PositionRelay.LEGACY, 2, "Rest", pair -> {
            while (pair.mover().player.getAliveTicks() <= 20) tick(pair.mover()); // Minestom's first scheduled sync
            move(pair.mover(), 0.05, 0, 0);
            pair.watcher().sent.clear();
            while (pair.mover().player.getAliveTicks() % 60 != 59) tick(pair.mover());
            assertTrue(moves(pair.watcher(), pair.mover()).isEmpty(), "a twentieth of a block waits");
            tick(pair.mover());
            assertEquals(1, moves(pair.watcher(), pair.mover()).size(), "the sixtieth tick sends what is left");
        });
    }

    @Test
    void offTheBeatOnlyAKnockbackSends() {
        with(PlayerConfig.PositionRelay.LEGACY, 2, "Beat", pair -> {
            move(pair.mover(), 0.5, 0, 0);
            tick(pair.mover());
            assertTrue(moves(pair.watcher(), pair.mover()).isEmpty(), "off the beat");
            ((PolypPlayer) pair.mover().player).relayImpulse();
            assertEquals(1, moves(pair.watcher(), pair.mover()).size(), "the knockback's tick looks at once");
        });
    }

    @Test
    void aJumpGoesOutAbsolute() {
        with(PlayerConfig.PositionRelay.LEGACY, 2, "Jump", pair -> {
            pair.mover().player.refreshOnGround(true);
            throughBeat(pair.mover());
            pair.watcher().sent.clear();
            move(pair.mover(), 0, 0.42, 0);
            pair.mover().player.refreshOnGround(false);
            tick(pair.mover());
            List<ServerPacket> sent = moves(pair.watcher(), pair.mover());
            assertEquals(1, sent.size(), "the jump's tick, off the beat: " + sent);
            assertInstanceOf(EntityPositionSyncPacket.class, sent.getFirst(), "absolute: the on-ground state changed");
        });
    }

    @Test
    void hypixelJumpsGoOutRelative() {
        with(PlayerConfig.PositionRelay.LEGACY.withGroundSync(false), 2, "Hop", pair -> {
            pair.mover().player.refreshOnGround(true);
            throughBeat(pair.mover());
            pair.watcher().sent.clear();
            move(pair.mover(), 0, 0.42, 0);
            pair.mover().player.refreshOnGround(false);
            tick(pair.mover());
            List<ServerPacket> sent = moves(pair.watcher(), pair.mover());
            assertEquals(1, sent.size(), "still looked at off the beat: " + sent);
            assertInstanceOf(EntityPositionPacket.class, sent.getFirst(), "relative");
        });
    }

    @Test
    void aTurnAloneSendsTheLook() {
        with(PlayerConfig.PositionRelay.LEGACY, 2, "Turn", pair -> {
            Pos at = pair.mover().player.getPosition();
            pair.mover().player.refreshPosition(at.withYaw(at.yaw() + 2f), false, true);
            throughBeat(pair.mover());
            assertTrue(moves(pair.watcher(), pair.mover()).isEmpty(), "two degrees: under 4/256 of a turn");
            pair.mover().player.refreshPosition(at.withYaw(at.yaw() + 10f), false, true);
            throughBeat(pair.mover());
            List<ServerPacket> sent = moves(pair.watcher(), pair.mover());
            assertEquals(1, sent.size(), "ten: " + sent);
            assertInstanceOf(EntityRotationPacket.class, sent.getFirst(), "the look alone");
        });
    }

    @Test
    void aLateViewerSpawnsAtTheRelay() {
        with(PlayerConfig.PositionRelay.LEGACY, 2, "Late", pair -> {
            move(pair.mover(), 0.05, 0, 0);
            throughBeat(pair.mover());
            FakePlayer late = FakePlayer.connect(pair.mover().player.getInstance(), new Pos(2.5, 64, 0.5), "LtLate");
            try {
                int id = pair.mover().player.getEntityId();
                SpawnEntityPacket spawn = late.sent(SpawnEntityPacket.class).stream()
                        .filter(p -> p.entityId() == id).findFirst().orElseThrow();
                assertEquals(0.5, spawn.position().x(), 1e-9, "where the watchers were last sent");
            } finally {
                late.player.remove();
            }
        });
    }

    @Test
    void everyMoveRelaysEveryMove() {
        with(PlayerConfig.PositionRelay.EVERY_MOVE, 1, "Every", pair -> {
            for (int i = 0; i < 3; i++) {
                move(pair.mover(), 0.01, 0, 0);
                tick(pair.mover());
            }
            assertEquals(3, moves(pair.watcher(), pair.mover()).size(), "a hundredth of a block, every tick");
        });
    }
}
