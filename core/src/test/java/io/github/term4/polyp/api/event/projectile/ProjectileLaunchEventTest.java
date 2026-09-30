package io.github.term4.polyp.api.event.projectile;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectileLaunchEventTest extends HeadlessServerTest {

    /** The projectile has no world yet at launch; a shooter with no shard binding still names one. */
    @Test
    void worldIsTheShooters() {
        List<MechanicsWorld> seen = new ArrayList<>();
        EventNode<Event> probe = EventNode.all("test:launch-world");
        probe.addListener(ProjectileLaunchEvent.class, e -> seen.add(e.world()));
        MinecraftServer.getGlobalEventHandler().addChild(probe);
        LivingEntity shooter = zombie(new Pos(740.5, 65, 740.5, 0f, 0f));
        try {
            ProjectileConfig cfg = Vanilla18.projectiles();
            ProjectileEntity shot = new ProjectileSystem(Polyp.getInstance(), cfg)
                    .launch(ProjectileSnapshot.of(shooter, Arrow.INSTANCE).withConfig(cfg));
            assertNotNull(shot);
            assertEquals(List.of(MechanicsWorld.of(instance)), seen);
            shot.remove();
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(probe);
            shooter.remove();
        }
    }
}
