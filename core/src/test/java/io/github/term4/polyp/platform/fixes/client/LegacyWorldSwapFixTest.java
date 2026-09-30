package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 1.8 client changing instance within one dimension hops through another before the new world's first spawn. */
class LegacyWorldSwapFixTest extends HeadlessServerTest {

    private static final Pos AT = new Pos(0.5, 65, 0.5);

    @BeforeAll
    static void setUp() {
        FixesSystem.install(polyp, FixesConfig.builder().legacyWorldSwap(FixToggleConfig.on()).build());
    }

    @Test
    void legacySwitchHops() {
        InstanceContainer next = world();
        LivingEntity resident = resident(next);
        try {
            List<ServerPacket> got = swap("SwapLegacy", 47, next);
            List<RespawnPacket> respawns = got.stream().filter(RespawnPacket.class::isInstance).map(RespawnPacket.class::cast).toList();
            assertEquals(2, respawns.size(), "away and back");
            var registry = MinecraftServer.getDimensionTypeRegistry();
            assertEquals(registry.getId(DimensionType.THE_NETHER), respawns.get(0).playerSpawnInfo().dimensionType());
            assertEquals(registry.getId(next.getDimensionType()), respawns.get(1).playerSpawnInfo().dimensionType());
            int back = got.indexOf(respawns.get(1));
            int spawn = indexOf(got, p -> p instanceof SpawnEntityPacket s && s.entityId() == resident.getEntityId());
            assertTrue(spawn > back, "the new world's entities spawn into the fresh one");
            assertTrue(indexOf(got, p -> p instanceof WindowItemsPacket) > back, "the rebuilt player gets its inventory");
        } finally {
            resident.remove();
        }
    }

    @Test
    void modernSwitchStays() {
        InstanceContainer next = world();
        List<ServerPacket> got = swap("SwapModern", MinecraftServer.PROTOCOL_VERSION, next);
        assertEquals(-1, indexOf(got, p -> p instanceof RespawnPacket), "a modern client refills in place");
    }

    private static InstanceContainer world() {
        InstanceContainer world = flatInstance(null);
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) world.loadChunk(x, z).join();
        return world;
    }

    private static LivingEntity resident(InstanceContainer world) {
        LivingEntity zombie = new LivingEntity(EntityType.ZOMBIE);
        zombie.setInstance(world, AT.add(3, 0, 3)).join();
        return zombie;
    }

    /** Joins {@link #instance}, moves to {@code next}, and returns what the client got from the move on. */
    private static List<ServerPacket> swap(String name, int protocol, InstanceContainer next) {
        FakePlayer p = FakePlayer.connect(instance, AT, name);
        try {
            polyp.clientInfo().setProtocol(p.player, protocol);
            p.sent.clear();
            p.player.setInstance(next, AT).join();
            return p.sent(ServerPacket.class);
        } finally {
            p.player.remove();
        }
    }

    private static int indexOf(List<ServerPacket> packets, Predicate<ServerPacket> match) {
        for (int i = 0; i < packets.size(); i++) if (match.test(packets.get(i))) return i;
        return -1;
    }
}
