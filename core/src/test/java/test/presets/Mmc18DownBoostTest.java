package test.presets;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.explosion.BlockBreaking;
import io.github.term4.polyp.mechanics.explosion.ExplosionSystem;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.presets.mmc18.Explosion;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.presets.mmc18.Projectiles;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The straight-down MineMen fireball boost is one number wherever you stand: the blast is server-authoritative,
 * so the spawn must keep its double precision. In lockstep the 1/32 wire truncation dropped it 0.026 and pushed
 * it off-axis by the sub-block remainder, and the boost read 1.667-1.674 by standing spot.
 */
class Mmc18DownBoostTest extends HeadlessServerTest {

    private static final double WIRE = 1.0 / 8000; // one short of velocity

    @BeforeAll
    static void installExplosions() {
        ExplosionSystem.install(polyp);
    }

    // a private world with the whole MMC18 profile at instance scope: the blast resolves EXPLOSION off the
    // profile chain before the installed config, so a global another test left behind would price it instead
    private static Instance world() {
        return flatInstance(Preset.MMC18.profile().toBuilder()
                .mutate(MechanicsKeys.EXPLOSION, ex -> Explosion.config().toBuilder().blockBreaking((BlockBreaking) null).build())
                .build());
    }

    /** Fires straight down from {@code (x, 64, z)} and returns the boost the wire carried, in b/t. */
    private static Vec boostAt(double x, double z, String tag) {
        FakePlayer p = FakePlayer.connect(world(), new Pos(x, 64, z, 0f, 90f), "Down" + tag);
        try {
            ProjectileConfig cfg = Projectiles.config();
            ProjectileEntity fb = new ProjectileSystem(Polyp.getInstance(), cfg)
                    .launch(ProjectileSnapshot.of(p.player, Fireball.INSTANCE).withConfig(cfg));
            assertNotNull(fb);
            awaitSpawn(fb);
            p.sent.clear();
            for (int t = 1; t <= 20 && !fb.isRemoved(); t++) fb.tick(t * 50L);
            return p.sent.stream().filter(EntityVelocityPacket.class::isInstance).map(EntityVelocityPacket.class::cast)
                    .filter(k -> k.entityId() == p.player.getEntityId())
                    .map(EntityVelocityPacket::velocity).filter(v -> v.y() != 0)
                    .findFirst().orElseThrow(() -> new AssertionError("no boost delivered at " + tag));
        } finally {
            p.player.remove();
        }
    }

    @Test
    void theBoostIsOneNumberEverywhere() {
        double[][] spots = {{8.5, 8.5}, {20.3, -9.7}, {20.999, -10.001}, {20.25, -10.75}};
        for (int i = 0; i < spots.length; i++) {
            String at = spots[i][0] + "," + spots[i][1];
            Vec v = boostAt(spots[i][0], spots[i][1], String.valueOf((char) ('A' + i))); // a name, not a coordinate
            assertEquals(1.6655, v.y(), WIRE, "vertical at " + at);
            // dead-centre blast: the hurt-KB horizontal is the coincident diagonal, never a leaked offset
            assertEquals(-0.3728, v.x(), WIRE * 2, "x at " + at);
            assertEquals(-0.3728, v.z(), WIRE * 2, "z at " + at);
        }
    }
}
