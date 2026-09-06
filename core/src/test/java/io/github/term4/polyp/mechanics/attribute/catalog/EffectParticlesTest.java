package io.github.term4.polyp.mechanics.attribute.catalog;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The swirl is one particle in the 1.8 blend of the showing effects; a silent one adds nothing, a removed one leaves. */
class EffectParticlesTest extends HeadlessServerTest {

    private static int swirlColor(LivingEntityMeta meta) {
        assertEquals(1, meta.getEffectParticles().size(), "one swirl, whatever the effects");
        return ((Particle.EntityEffect) meta.getEffectParticles().getFirst()).color().asARGB() & 0xFFFFFF;
    }

    @Test
    void effectsSwirlInTheLegacyBlend() {
        LivingEntity z = zombie(new Pos(0, 64, 120));
        LivingEntityMeta meta = (LivingEntityMeta) z.getEntityMeta();
        z.addEffect(new Potion(PotionEffect.STRENGTH, 0, 200, Potion.PARTICLES_FLAG | Potion.ICON_FLAG));
        MinecraftServer.getSchedulerManager().processTick();
        assertEquals(9643043, swirlColor(meta), "1.8 strength, not the modern gold");
        assertFalse(meta.isPotionEffectAmbient());

        z.addEffect(new Potion(PotionEffect.SPEED, 0, 200, Potion.PARTICLES_FLAG));
        MinecraftServer.getSchedulerManager().processTick();
        assertEquals(8874356, swirlColor(meta), "strength and speed averaged per channel, as PotionBrewer.a does");

        z.addEffect(new Potion(PotionEffect.HASTE, 0, 200, 0)); // no particles asked for
        MinecraftServer.getSchedulerManager().processTick();
        assertEquals(8874356, swirlColor(meta), "a silent effect adds nothing");

        z.removeEffect(PotionEffect.STRENGTH);
        z.removeEffect(PotionEffect.HASTE);
        MinecraftServer.getSchedulerManager().processTick();
        assertEquals(8171462, swirlColor(meta), "1.8 speed alone");

        z.removeEffect(PotionEffect.SPEED);
        MinecraftServer.getSchedulerManager().processTick();
        assertTrue(meta.getEffectParticles().isEmpty(), "removed, the swirl goes");

        z.addEffect(new Potion(PotionEffect.REGENERATION, 0, 200, Potion.AMBIENT_FLAG | Potion.PARTICLES_FLAG));
        MinecraftServer.getSchedulerManager().processTick();
        assertTrue(meta.isPotionEffectAmbient(), "a beacon's effect swirls faint");
        z.remove();
    }
}
