package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.entities.arrow.StuckArrows;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.presets.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A damaging arrow hit puts one arrow in the body's metadata, where every viewer draws it; a clear pulls it out. */
class StuckArrowsTest extends HeadlessServerTest {

    @Test
    void arrowSticksInBody() {
        LivingEntity victim = zombie(new Pos(300.5, 65, 305.5));
        victim.setHealth(20f);
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, new Pos(300.5, 65, 300.5, 0f, 0f)).join();
        try {
            var snap = ProjectileSnapshot.of(shooter, Arrow.INSTANCE)
                    .withConfig(Vanilla18.projectiles()).withItem(ItemStack.of(Material.BOW));
            ProjectileEntity arrow = new ProjectileSystem(Polyp.getInstance(), Vanilla18.projectiles()).launch(snap);
            assertNotNull(arrow);
            awaitSpawn(arrow);
            for (int t = 1; t <= 40 && !arrow.isRemoved(); t++) arrow.tick(t * 50L);
            assertTrue(arrow.isRemoved(), "the arrow must reach the victim");
            assertEquals(1, StuckArrows.count(victim), "one arrow in the body");
            assertEquals(1, ((LivingEntityMeta) victim.getEntityMeta()).getArrowCount(), "on the metadata viewers draw");
            StuckArrows.clear(victim);
            assertEquals(0, ((LivingEntityMeta) victim.getEntityMeta()).getArrowCount(), "a reset pulls it out");
        } finally {
            shooter.remove();
            victim.remove();
        }
    }
}
