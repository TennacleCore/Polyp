package io.github.term4.polyp.mechanics.projectile.entities;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 1.8 client despawns its own stuck arrow at 1200 ticks in ground; an arrow the server keeps longer is re-spawned to it under that window. */
class LegacyStuckArrowRearmTest extends HeadlessServerTest {

    @Test
    void aStuckArrowKeptLongerThanTheClientCountsIsRespawnedForLegacyViewers() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(700.5, 66, 700.5, 0f, 90f), "StuckShooter"); // looking straight down
        FakePlayer legacy = FakePlayer.connect(instance, new Pos(702.5, 65, 700.5), "StuckLegacy");
        FakePlayer modern = FakePlayer.connect(instance, new Pos(698.5, 65, 700.5), "StuckModern");
        ((OptimizedPlayer) legacy.player).compat().setLegacyClient(true);
        var config = Vanilla18.projectiles();
        ProjectileEntity arrow = new ProjectileSystem(Polyp.getInstance(), config)
                .launch(ProjectileSnapshot.of(shooter.player, Arrow.INSTANCE).withConfig(config));
        assertNotNull(arrow);
        awaitSpawn(arrow);
        try {
            arrow.setStuckDespawnTicks(0); // the server keeps it forever
            if (!arrow.getViewers().contains(legacy.player)) arrow.addViewer(legacy.player);
            if (!arrow.getViewers().contains(modern.player)) arrow.addViewer(modern.player);
            int t = 1;
            for (; t <= 200 && !arrow.isStuck(); t++) arrow.tick(t * 50L);
            assertTrue(arrow.isStuck(), "the arrow stuck in the floor");
            legacy.sent.clear();
            modern.sent.clear();
            for (int i = 0; i < 1001; i++, t++) arrow.tick(t * 50L);
            long legacySpawns = legacy.sent(SpawnEntityPacket.class).stream().filter(s -> s.entityId() == arrow.getEntityId()).count();
            long modernSpawns = modern.sent(SpawnEntityPacket.class).stream().filter(s -> s.entityId() == arrow.getEntityId()).count();
            assertEquals(1, legacySpawns, "one fresh spawn under the client's 1200-tick window");
            assertEquals(0, modernSpawns, "a modern client keeps the entity as long as the server does");
        } finally {
            arrow.remove();
            shooter.player.remove();
            legacy.player.remove();
            modern.player.remove();
        }
    }
}
