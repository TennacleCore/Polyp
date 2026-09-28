package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chunk a 1.8 client already holds comes back as a new object: its entities are spawned afresh behind it. */
class LegacyChunkReloadFixTest extends HeadlessServerTest {

    private static final Pos STAND = new Pos(40.5, 64, 40.5); // chunk 2,2

    @BeforeAll
    static void setUp() {
        FixesSystem.install(polyp, FixesConfig.builder().legacyChunkReload(FixToggleConfig.on()).build());
    }

    @Test
    void aReloadRespawnsItsEntities() {
        LivingEntity bystander = zombie(STAND);
        try {
            List<ServerPacket> got = reload("ReloadLegacy", 47, bystander);
            int chunk = indexOf(got, 0, p -> p instanceof ChunkDataPacket c && c.chunkX() == 2 && c.chunkZ() == 2);
            assertTrue(chunk >= 0, "the chunk went out again");
            int destroy = indexOf(got, chunk, p -> p instanceof DestroyEntitiesPacket d
                    && d.entityIds().contains(bystander.getEntityId()));
            assertTrue(destroy > chunk, "the destroy follows the chunk");
            int spawn = indexOf(got, destroy, p -> p instanceof SpawnEntityPacket s && s.entityId() == bystander.getEntityId());
            assertTrue(spawn > destroy, "and the fresh spawn follows the destroy");
        } finally {
            bystander.remove();
        }
    }

    @Test
    void aModernClientKeepsItsCopy() {
        LivingEntity bystander = zombie(STAND);
        try {
            List<ServerPacket> got = reload("ReloadModern", MinecraftServer.PROTOCOL_VERSION, bystander);
            assertTrue(indexOf(got, 0, p -> p instanceof ChunkDataPacket c && c.chunkX() == 2 && c.chunkZ() == 2) >= 0);
            assertEquals(-1, indexOf(got, 0, p -> p instanceof DestroyEntitiesPacket d
                    && d.entityIds().contains(bystander.getEntityId())), "a modern client refills its chunk in place");
        } finally {
            bystander.remove();
        }
    }

    /** Joins beside {@code bystander}, has their chunk sent again, and returns what the client got from then on. */
    private static List<ServerPacket> reload(String name, int protocol, LivingEntity bystander) {
        FakePlayer p = FakePlayer.connect(instance, STAND, name);
        try {
            Polyp.getInstance().clientInfo().setProtocol(p.player, protocol);
            assertTrue(bystander.isViewer(p.player), "the join spawned the bystander");
            p.player.onChunkBatchReceived(64f); // the fake client's ack: the join's batch lead would hold the rest
            p.sent.clear();
            p.player.sendChunk(instance.getChunk(2, 2));
            p.player.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
            return p.sent(ServerPacket.class);
        } finally {
            p.player.remove();
        }
    }

    private static int indexOf(List<ServerPacket> packets, int from, Predicate<ServerPacket> match) {
        for (int i = from; i < packets.size(); i++) if (match.test(packets.get(i))) return i;
        return -1;
    }
}
