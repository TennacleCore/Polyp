package io.github.term4.polyp.fx;

import io.github.term4.polyp.config.FieldFns;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

/**
 * <em>What</em> one recipient perceives, at the anchor {@link FxAudience} hands it - the other half of an
 * {@link FxHandler}. Pairing any effect with any audience is what keeps the two vocabularies additive rather
 * than a grid of prebuilt combinations.
 *
 * @see FxHandler#of(FxAudience, FxEffect)
 */
@FunctionalInterface
public interface FxEffect {

    /** @param seed one value per play, shared by every recipient, so a variant sound picks the same variant for all */
    void send(@NotNull FxContext ctx, @NotNull Player to, @NotNull Point at, long seed);

    static @NotNull FxEffect sound(@NotNull SoundEvent sound, @NotNull Sound.Source source, float volume, float pitch) {
        return (ctx, to, at, seed) -> to.sendPacket(new SoundEffectPacket(sound, source, at, volume, pitch, seed));
    }

    static @NotNull FxEffect particle(@NotNull Particle particle, int count, double spread, float speed) {
        return (ctx, to, at, seed) -> to.sendPacket(new ParticlePacket(particle, at.x(), at.y(), at.z(),
                (float) spread, (float) spread, (float) spread, speed, count));
    }

    /** An animation on the context's source entity. */
    static @NotNull FxEffect animation(EntityAnimationPacket.@NotNull Animation animation) {
        return (ctx, to, at, seed) -> {
            if (ctx.source() != null) to.sendPacket(new EntityAnimationPacket(ctx.source().getEntityId(), animation));
        };
    }

    /** An animation on the context's target entity (hit feedback). */
    static @NotNull FxEffect targetAnimation(EntityAnimationPacket.@NotNull Animation animation) {
        return (ctx, to, at, seed) -> {
            if (ctx.target() != null) to.sendPacket(new EntityAnimationPacket(ctx.target().getEntityId(), animation));
        };
    }

    /**
     * The data vocabulary: EFFECTS here and AUDIENCES in {@link FxAudience}, composed by {@code to(audience,
     * effect)} - a new audience works with every effect and vice versa, so the two never multiply out. A bare
     * effect keeps the vanilla shard audience:
     * <pre>fx/polyp:pearl_teleport = to(everywhere, sound(entity.player.teleport, player, 1, 1))</pre>
     * Lives here, not on {@code Fx}, whose class init needs a running server.
     */
    static void registerFactories() {
        FxAudience.registerFactories();
        FieldFns.register(FxEffect.class, "sound(id, source, volume, pitch)", "a sound", args -> sound(
                soundOf(args.arity(4), 0), args.enumOf(1, Sound.Source.class), args.flt(2), args.flt(3)));
        FieldFns.register(FxEffect.class, "particle(id, count, spread, speed)", "a particle burst", args -> particle(
                particleOf(args.arity(4), 0), args.integer(1), args.dbl(2), args.flt(3)));
        FieldFns.register(FxEffect.class, "animation(name)", "an animation on the source entity",
                args -> animation(args.arity(1).enumOf(0, EntityAnimationPacket.Animation.class)));
        FieldFns.register(FxEffect.class, "target-animation(name)", "an animation on the target entity",
                args -> targetAnimation(args.arity(1).enumOf(0, EntityAnimationPacket.Animation.class)));

        FieldFns.register(FxHandler.class, "none", "plays nothing - silences this key", args -> FxHandler.NONE);
        FieldFns.register(FxHandler.class, "to(audience, effect)", "an effect delivered to an audience",
                args -> FxHandler.of(args.arity(2).of(0, FxAudience.class), args.of(1, FxEffect.class)));
        // a bare effect is the default audience, so the common case stays short
        for (String effect : FieldFns.names(FxEffect.class)) {
            FieldFns.register(FxHandler.class, effect, "shorthand for to(watchers, " + effect + "(...))",
                    args -> FxHandler.of(FxAudience.WATCHERS, FieldFns.build(FxEffect.class, effect, args)));
        }
    }

    private static SoundEvent soundOf(FieldFns.Args args, int i) {
        Key key = args.key(i);
        SoundEvent sound = SoundEvent.fromKey(key);
        if (sound == null) throw new IllegalArgumentException("unknown sound '" + key.asString() + "'");
        return sound;
    }

    private static Particle particleOf(FieldFns.Args args, int i) {
        Key key = args.key(i);
        Particle particle = Particle.fromKey(key);
        if (particle == null) throw new IllegalArgumentException("unknown particle '" + key.asString() + "'");
        return particle;
    }
}
