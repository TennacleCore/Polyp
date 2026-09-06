package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client simulates its own copy of an arrow against the world as it last saw it. A block that goes between the
 * spawn and the server's next step (a cage opening) stops the copy where the server's flies on, and a client-predicted
 * wire never corrects it - so the server re-sends the arrow the moment its step passes a cell it had expected to hit.
 */
class PhantomBlockResendTest extends HeadlessServerTest {

    private static final int WALL_Z = 704;

    @BeforeAll
    static void ground() {
        for (int cx = 43; cx <= 44; cx++) for (int cz = 43; cz <= 44; cz++) instance.loadChunk(cx, cz).join();
    }

    private static void wall(Block block) {
        for (int x = 699; x <= 701; x++) for (int y = 65; y <= 68; y++) instance.setBlock(x, y, WALL_Z, block);
    }

    // two blocks a tick, straight down z from just past the shooter's face
    private static ProjectileEntity shoot(FakePlayer shooter) {
        var snap = ProjectileSnapshot.of(shooter.player, Arrow.INSTANCE).withConfig(Vanilla18.projectiles())
                .withSpawnPos(new Pos(700.5, 66.6, 701.0)).withVelocity(new Vec(0, 0, 2.0));
        ProjectileEntity arrow = new ProjectileSystem(Polyp.getInstance(), Vanilla18.projectiles()).launch(snap);
        assertNotNull(arrow);
        awaitSpawn(arrow);
        return arrow;
    }

    private static int at(FakePlayer viewer, Predicate<ServerPacket> what) {
        List<SendablePacket> all = List.copyOf(viewer.sent);
        for (int i = 0; i < all.size(); i++) {
            if (what.test(SendablePacket.extractServerPacket(ConnectionState.PLAY, all.get(i)))) return i;
        }
        return -1;
    }

    @Test
    void aBlockGoneAheadRespawnsTheArrow() {
        wall(Block.STONE);
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(700.5, 65, 700.5, 0f, 0f), "PhantomShot");
        ProjectileEntity arrow = shoot(shooter);
        try {
            arrow.tick(50L); // a step short of the wall: the shooter's copy is about to hit it
            shooter.sent.clear();
            wall(Block.AIR); // gone before the server's next step, as a cage opens
            arrow.tick(100L);
            assertTrue(arrow.getPosition().z() > WALL_Z, "the server's arrow flies through: " + arrow.getPosition());
            int id = arrow.getEntityId();
            int gone = at(shooter, p -> p instanceof BlockChangePacket b && b.blockPosition().blockZ() == WALL_Z);
            int again = at(shooter, p -> p instanceof SpawnEntityPacket s && s.entityId() == id);
            assertTrue(gone >= 0, "the removal reached the shooter");
            assertTrue(again > gone, "and the arrow is re-sent after it, off the phantom wall: spawn " + again + ", removal " + gone);
        } finally {
            arrow.remove();
            shooter.player.remove();
            wall(Block.AIR);
        }
    }

    @Test
    void aWallThatStaysStopsBoth() {
        wall(Block.STONE);
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(700.5, 65, 700.5, 0f, 0f), "SolidShot");
        ProjectileEntity arrow = shoot(shooter);
        try {
            arrow.tick(50L);
            shooter.sent.clear();
            arrow.tick(100L);
            arrow.tick(150L);
            assertTrue(arrow.isStuck(), "the server's arrow sticks where the copy did");
            int id = arrow.getEntityId();
            assertEquals(-1, at(shooter, p -> p instanceof SpawnEntityPacket s && s.entityId() == id), "nothing to correct");
        } finally {
            arrow.remove();
            shooter.player.remove();
            wall(Block.AIR);
        }
    }
}
