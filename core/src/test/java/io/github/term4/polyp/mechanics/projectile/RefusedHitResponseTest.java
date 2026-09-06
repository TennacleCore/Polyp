package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.api.event.damage.DamageEvent;
import io.github.term4.polyp.api.event.projectile.ProjectileHitEvent;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig.HitResponse;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig.InvulnResponse;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hit the victim refuses: the per-hit override decides whether the arrow dies or flies on. */
class RefusedHitResponseTest extends HeadlessServerTest {

    /** An arrow at a victim whose every damage event a listener cancels; {@code response} rides the hit event. */
    private ProjectileEntity refusedShot(Pos victimAt, Pos from, HitResponse response) {
        LivingEntity victim = zombie(victimAt);
        victim.setHealth(20f);
        boolean[] struck = {false};
        EventNode<Event> node = EventNode.all("refused-hit");
        node.addListener(DamageEvent.class, e -> {
            if (e.target() == victim) e.cancel();
        });
        node.addListener(ProjectileHitEvent.class, e -> {
            if (e.target() != victim) return;
            struck[0] = true;
            e.invulnHit(InvulnResponse.of(response));
        });
        MinecraftServer.getGlobalEventHandler().addChild(node);
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, from).join();
        try {
            var snap = ProjectileSnapshot.of(shooter, Arrow.INSTANCE)
                    .withConfig(Vanilla18.projectiles()).withItem(ItemStack.of(Material.BOW));
            ProjectileEntity arrow = new ProjectileSystem(Polyp.getInstance(), Vanilla18.projectiles()).launch(snap);
            assertNotNull(arrow);
            awaitSpawn(arrow);
            for (int t = 1; t <= 4 && !arrow.isRemoved(); t++) arrow.tick(t * 50L);
            assertTrue(struck[0], "the arrow reached the victim");
            assertEquals(20f, victim.getHealth(), "and the hit was refused");
            return arrow;
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
            shooter.remove();
            victim.remove();
        }
    }

    @Test
    void destroyRemovesIt() {
        ProjectileEntity arrow = refusedShot(new Pos(300.5, 65, 305.5), new Pos(300.5, 65, 300.5, 0f, 0f), HitResponse.DESTROY);
        assertTrue(arrow.isRemoved(), "gone on the refusal");
    }

    @Test
    void passThroughFliesOn() {
        ProjectileEntity arrow = refusedShot(new Pos(320.5, 65, 325.5), new Pos(320.5, 65, 320.5, 0f, 0f), HitResponse.PASS_THROUGH);
        assertFalse(arrow.isRemoved(), "through and beyond");
        arrow.remove();
    }
}
