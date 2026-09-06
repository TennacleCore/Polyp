package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.api.event.damage.DamageEvent;
import io.github.term4.polyp.api.event.projectile.ProjectileHitEvent;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxRegistry;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig.HitResponse;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig.InvulnResponse;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
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

/** A hit the victim refuses: the per-hit override decides whether the arrow dies or flies on, and a death there is silent. */
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

    /** The fx a player's arrow raises at the victim: the bowhit for everyone, the ding for the shooter. */
    private int[] shot(Pos victimAt, Pos from, boolean refused) {
        int[] heard = {0, 0};
        MechanicsProfile previous = Polyp.getInstance().profiles().global();
        Polyp.getInstance().profiles().setGlobal(previous.toBuilder().set(MechanicsKeys.FX, FxRegistry.empty()
                .register(Fx.ARROW_HIT, ctx -> heard[0]++)
                .register(Fx.ARROW_HIT_PLAYER, ctx -> heard[1]++)).build());
        LivingEntity victim = zombie(victimAt);
        victim.setHealth(20f);
        EventNode<Event> node = EventNode.all("refused-shot");
        if (refused) {
            node.addListener(DamageEvent.class, e -> {
                if (e.target() == victim) e.cancel();
            });
            node.addListener(ProjectileHitEvent.class, e -> {
                if (e.target() == victim) e.invulnHit(InvulnResponse.of(HitResponse.DESTROY));
            });
        }
        MinecraftServer.getGlobalEventHandler().addChild(node);
        FakePlayer shooter = FakePlayer.connect(instance, from, "Archer" + (refused ? "R" : "L"));
        try {
            var snap = ProjectileSnapshot.of(shooter.player, Arrow.INSTANCE)
                    .withConfig(Vanilla18.projectiles()).withItem(ItemStack.of(Material.BOW));
            ProjectileEntity arrow = new ProjectileSystem(Polyp.getInstance(), Vanilla18.projectiles()).launch(snap);
            assertNotNull(arrow);
            awaitSpawn(arrow);
            for (int t = 1; t <= 6 && !arrow.isRemoved(); t++) arrow.tick(t * 50L);
            assertTrue(arrow.isRemoved(), "the arrow ended at the victim");
            return heard;
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
            Polyp.getInstance().profiles().setGlobal(previous);
            shooter.player.remove();
            victim.remove();
        }
    }

    @Test
    void aRefusedArrowIsSilent() {
        int[] landed = shot(new Pos(340.5, 65, 345.5), new Pos(340.5, 65, 340.5, 0f, 0f), false);
        assertEquals(1, landed[0], "a landed hit: the bowhit");
        assertEquals(1, landed[1], "and the shooter's ding");
        int[] refused = shot(new Pos(360.5, 65, 365.5), new Pos(360.5, 65, 360.5, 0f, 0f), true);
        assertEquals(0, refused[0], "a refused hit: no bowhit");
        assertEquals(0, refused[1], "and no ding");
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
