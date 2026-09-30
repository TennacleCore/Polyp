package test.presets;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How long a fireball lives around its thrower and the world's edge, per preset. */
class FireballLifeTest extends HeadlessServerTest {

    private static final ProjectileConfig HYPIXEL = io.github.term4.polyp.presets.hypixel.Projectiles.config();
    private static final ProjectileConfig MMC18 = io.github.term4.polyp.presets.mmc18.Projectiles.config();
    private static final ProjectileConfig MODERN = io.github.term4.polyp.presets.vanilla.Projectiles.config();

    private static ProjectileEntity launch(Entity shooter, ProjectileConfig config) {
        var snap = ProjectileSnapshot.of(shooter, Fireball.INSTANCE).withConfig(config);
        ProjectileEntity fb = new ProjectileSystem(Polyp.getInstance(), config).launch(snap);
        assertNotNull(fb);
        awaitSpawn(fb);
        return fb;
    }

    // the instance clock the corpse is timed on moves with the fireball
    private static void tick(ProjectileEntity fb, int ticks) {
        for (int i = 0; i < ticks && !fb.isRemoved(); i++) {
            EventDispatcher.call(new InstanceTickEvent(instance, 0, 0));
            fb.tick(0);
        }
    }

    /** Stands the thrower just above its own rising fireball at {@code age} ticks: whether it hit them. */
    private static boolean hitsItsThrower(ProjectileConfig config, int age, double x, String name) {
        FakePlayer thrower = FakePlayer.connect(instance, new Pos(x, 100, 800.5, 0f, -90f), name);
        ProjectileEntity fb = launch(thrower.player, config);
        try {
            tick(fb, age);
            assertFalse(fb.isRemoved(), name + ": still flying at " + age);
            Pos at = fb.getPosition();
            thrower.player.teleport(new Pos(at.x(), at.y() + 0.8, at.z(), 0f, -90f)).join();
            tick(fb, 6);
            return fb.isRemoved();
        } finally {
            fb.remove();
            thrower.player.remove();
        }
    }

    @Test
    void theTopEndsIt() {
        int top = instance.getCachedDimensionType().minY() + instance.getCachedDimensionType().height();
        FakePlayer thrower = FakePlayer.connect(instance, new Pos(770.5, top - 3, 770.5, 0f, -90f), "FbTop");
        try {
            ProjectileEntity old = launch(thrower.player, Vanilla18.projectiles());
            tick(old, 10);
            assertTrue(old.isRemoved(), "1.8: gone past " + top + ", at " + old.getPosition().y());
            ProjectileEntity modern = launch(thrower.player, MODERN);
            tick(modern, 10);
            assertFalse(modern.isRemoved(), "26.1 flies on: " + modern.getPosition().y());
            assertTrue(modern.getPosition().y() > top);
            modern.remove();
        } finally {
            thrower.player.remove();
        }
    }

    @Test
    void goesWithTheCorpse() {
        record Case(String name, ProjectileConfig config, boolean dies) {}
        double x = 780.5;
        for (Case c : new Case[]{new Case("vanilla18", Vanilla18.projectiles(), true),
                new Case("hypixel", HYPIXEL, false), new Case("mmc18", MMC18, false)}) {
            FakePlayer thrower = FakePlayer.connect(instance, new Pos(x, 100, 780.5, 0f, -90f), "FbDie" + c.name().charAt(0));
            ProjectileEntity fb = launch(thrower.player, c.config());
            try {
                tick(fb, 2);
                thrower.player.kill();
                tick(fb, 19);
                assertFalse(fb.isRemoved(), c.name() + ": the corpse's last tick");
                tick(fb, 1);
                assertEquals(c.dies(), fb.isRemoved(), c.name() + ": the corpse gone");
            } finally {
                fb.remove();
                thrower.player.remove();
            }
            x += 10;
        }
    }

    @Test
    void aLeftThrowerEndsIt() {
        FakePlayer thrower = FakePlayer.connect(instance, new Pos(760.5, 100, 790.5, 0f, -90f), "FbLeft");
        ProjectileEntity fb = launch(thrower.player, Vanilla18.projectiles());
        try {
            tick(fb, 2);
            thrower.player.remove();
            tick(fb, 1);
            assertTrue(fb.isRemoved());
        } finally {
            fb.remove();
        }
    }

    @Test
    void theThrowerWindow() {
        assertFalse(hitsItsThrower(Vanilla18.projectiles(), 10, 820.5, "FbV10"), "1.8: safe inside 25 ticks");
        assertTrue(hitsItsThrower(Vanilla18.projectiles(), 27, 830.5, "FbV27"), "1.8: hit after 25");
        assertFalse(hitsItsThrower(HYPIXEL, 20, 840.5, "FbH20"), "hypixel: safe inside 25");
        assertTrue(hitsItsThrower(HYPIXEL, 27, 850.5, "FbH27"), "hypixel: hit after 25");
        assertFalse(hitsItsThrower(MMC18, 60, 860.5, "FbM60"), "mmc18: never");
    }
}
