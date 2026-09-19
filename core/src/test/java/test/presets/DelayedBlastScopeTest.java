package test.presets;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.explosion.ExplosionSystem;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.FireballEntity;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A BedWars fireball detonates a tick after impact, by which time it is removed and has no instance - so the
 * blast must take its config from the world it was handed, not from the source entity, or a preset set anywhere
 * below global is silently skipped.
 */
class DelayedBlastScopeTest extends HeadlessServerTest {

    @BeforeAll
    static void installExplosions() {
        ExplosionSystem.install(polyp);
    }

    /** Fires straight down and returns the damage the delayed detonation deals the thrower. */
    private static float selfBlastDamage(double x) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) instance.setBlock((int) x + dx, 99, 10 + dz, Block.STONE);
        FakePlayer p = FakePlayer.connect(instance, new Pos(x, 100, 10.5, 0f, 90f), "Blast" + (int) x);
        try {
            ProjectileConfig cfg = polyp.profiles().resolve(p.player, MechanicsKeys.PROJECTILES);
            FireballEntity fb = (FireballEntity) new ProjectileSystem(polyp, cfg)
                    .launch(ProjectileSnapshot.of(p.player, Fireball.INSTANCE).withConfig(cfg));
            assertNotNull(fb);
            awaitSpawn(fb);
            Point center = null;
            for (int t = 1; t <= 30 && !fb.isRemoved(); t++) { center = fb.getPosition(); fb.tick(t * 50L); }
            assertNull(fb.getInstance(), "the detonation runs on a removed fireball");
            float before = p.player.getHealth();
            fb.detonate(instance, center, null); // what the scheduled next-tick task runs
            return before - p.player.getHealth();
        } finally {
            p.player.remove();
        }
    }

    @Test
    void theLobbyPresetSurvives() {
        Polyp polyp = Polyp.getInstance();
        MechanicsProfile globalBefore = polyp.profiles().global();
        MechanicsProfile instanceBefore = polyp.profiles().instance(instance);
        try {
            polyp.profiles().setGlobal(Preset.HYPIXEL_BEDWARS.profile());
            polyp.profiles().setInstance(instance, null);
            assertEquals(2.0f, selfBlastDamage(920.5), 0.001f, "BedWars' flat blast, straight from global");

            // the same preset one scope down, under a global that prices blasts on a falloff curve
            polyp.profiles().setGlobal(Preset.MMC18.profile());
            polyp.profiles().setInstance(instance, Preset.HYPIXEL_BEDWARS.profile());
            assertEquals(2.0f, selfBlastDamage(960.5), 0.001f, "the instance scope still answers after the delay");
        } finally {
            polyp.profiles().setInstance(instance, instanceBefore);
            polyp.profiles().setGlobal(globalBefore);
        }
    }
}
