package io.github.term4.polyp.mechanics.attribute.catalog;

import io.github.term4.polyp.Polyp;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.AlphaColor;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityPotionAddEvent;
import net.minestom.server.event.entity.EntityPotionRemoveEvent;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.TimedPotion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The swirl other players see around an entity with effects. Vanilla mirrors the active effects into the living
 * metadata - one {@code entity_effect} particle in each effect's color, faint when the effect is ambient, and the
 * ambience flag when every one is - and Minestom's effect packets tell only the affected client. Refreshed a tick
 * after every add or remove, once the effect list has settled ({@code EntityPotionAddEvent} fires ahead of the add).
 */
public final class EffectParticles {

    private static final int AMBIENT_ALPHA = 38; // vanilla's 0.15
    private static final Particle.EntityEffect SWIRL = (Particle.EntityEffect) Particle.fromKey(Key.key("minecraft:entity_effect"));

    private EffectParticles() {}

    public static void install(@NotNull Polyp polyp) {
        EventNode<Event> node = EventNode.all("polyp:effect-particles");
        node.addListener(EntityPotionAddEvent.class, e -> later(e.getEntity()));
        node.addListener(EntityPotionRemoveEvent.class, e -> later(e.getEntity()));
        polyp.install(node);
    }

    private static void later(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            if (!living.isRemoved()) refresh(living);
        });
    }

    /** Writes {@code living}'s swirl off its effects now. */
    public static void refresh(@NotNull LivingEntity living) {
        if (!(living.getEntityMeta() instanceof LivingEntityMeta meta)) return;
        List<Particle> particles = new ArrayList<>();
        boolean ambient = true;
        for (TimedPotion timed : living.getActiveEffects()) {
            Potion potion = timed.potion();
            if (!potion.hasParticles()) continue;
            ambient &= potion.isAmbient();
            particles.add(swirl(potion));
        }
        meta.setEffectParticles(particles);
        meta.setPotionEffectAmbient(!particles.isEmpty() && ambient);
    }

    /** The effect's own color, faint when ambient. */
    public static @NotNull Particle swirl(@NotNull Potion potion) {
        int alpha = potion.isAmbient() ? AMBIENT_ALPHA : 255;
        return SWIRL.withColor(new AlphaColor((alpha << 24) | (potion.effect().color() & 0xFFFFFF)));
    }
}
