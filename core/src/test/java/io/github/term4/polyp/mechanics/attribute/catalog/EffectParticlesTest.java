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

/** An effect with particles swirls in its color on the living metadata; a silent one and a removed one do not. */
class EffectParticlesTest extends HeadlessServerTest {

    @Test
    void effectsSwirlOnTheMetadata() {
        LivingEntity z = zombie(new Pos(0, 64, 120));
        LivingEntityMeta meta = (LivingEntityMeta) z.getEntityMeta();
        z.addEffect(new Potion(PotionEffect.STRENGTH, 0, 200, Potion.PARTICLES_FLAG | Potion.ICON_FLAG));
        MinecraftServer.getSchedulerManager().processTick();
        assertEquals(1, meta.getEffectParticles().size(), "one swirl for the one effect");
        Particle.EntityEffect swirl = (Particle.EntityEffect) meta.getEffectParticles().getFirst();
        assertEquals(PotionEffect.STRENGTH.color() & 0xFFFFFF, swirl.color().asARGB() & 0xFFFFFF, "in the effect's color");
        assertFalse(meta.isPotionEffectAmbient());

        z.addEffect(new Potion(PotionEffect.SPEED, 0, 200, 0)); // no particles asked for
        MinecraftServer.getSchedulerManager().processTick();
        assertEquals(1, meta.getEffectParticles().size(), "a silent effect adds no swirl");

        z.removeEffect(PotionEffect.STRENGTH);
        MinecraftServer.getSchedulerManager().processTick();
        assertTrue(meta.getEffectParticles().isEmpty(), "removed, the swirl goes");

        z.addEffect(new Potion(PotionEffect.REGENERATION, 0, 200, Potion.AMBIENT_FLAG | Potion.PARTICLES_FLAG));
        MinecraftServer.getSchedulerManager().processTick();
        assertTrue(meta.isPotionEffectAmbient(), "a beacon's effect swirls faint");
        z.remove();
    }
}
