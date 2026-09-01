package io.github.term4.polyp.fx;

import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Where + who an {@link FxHandler} plays for: a {@code position} in a shard-scoped {@link MechanicsWorld}, an optional
 * {@code source} entity (entity animations ride its viewers), and an optional {@code target} (hit feedback). The emit
 * helpers route through the world / the source's viewers, so the audience follows the shard.
 */
public final class FxContext {

    private final MechanicsWorld world;
    private final Point position;
    private final @Nullable Entity source;
    private final @Nullable Entity target;
    private final @Nullable Object detail;

    private FxContext(MechanicsWorld world, Point position, @Nullable Entity source, @Nullable Entity target,
                      @Nullable Object detail) {
        this.world = world;
        this.position = position;
        this.source = source;
        this.target = target;
        this.detail = detail;
    }

    /** At {@code source}'s position, with animations riding its viewers. */
    public static @NotNull FxContext of(@NotNull Entity source) {
        return new FxContext(MechanicsWorld.of(source), source.getPosition(), source, null, null);
    }

    /** From {@code source} onto {@code target} - hit feedback ({@code source} = attacker, {@code target} = victim). */
    public static @NotNull FxContext of(@NotNull Entity source, @NotNull Entity target) {
        return new FxContext(MechanicsWorld.of(source), source.getPosition(), source, target, null);
    }

    /** A positional fx at {@code position} in {@code world} (no source entity). */
    public static @NotNull FxContext at(@NotNull MechanicsWorld world, @NotNull Point position) {
        return new FxContext(world, position, null, null, null);
    }

    /** A positional fx with a {@code source} for registry scope resolution (an explosion at its center, scoped by its igniter). */
    public static @NotNull FxContext at(@NotNull MechanicsWorld world, @NotNull Point position, @Nullable Entity source) {
        return new FxContext(world, position, source, null, null);
    }

    /** This context re-scoped to another world, for an audience that spans a layered family. */
    public @NotNull FxContext withWorld(@NotNull MechanicsWorld other) {
        return new FxContext(other, position, source, target, detail);
    }

    /** This context with a type-specific payload attached (the stepped-on block). */
    public @NotNull FxContext withDetail(@Nullable Object detail) {
        return new FxContext(world, position, source, target, detail);
    }

    public @NotNull MechanicsWorld world() { return world; }
    public @NotNull Point position() { return position; }
    public @Nullable Entity source() { return source; }
    public @Nullable Entity target() { return target; }

    /** Type-specific payload from the producer (the stepped-on block), or {@code null}. */
    public @Nullable Object detail() { return detail; }

    /** The {@link #detail()} payload when it is an instance of {@code type}, else {@code null}. */
    public <T> @Nullable T detail(@NotNull Class<T> type) {
        return type.isInstance(detail) ? type.cast(detail) : null;
    }

    /** Delivers {@code effect} to {@code audience} from this context - the one route every helper below takes. */
    public void emit(@NotNull FxAudience audience, @NotNull FxEffect effect) {
        FxHandler.of(audience, effect).play(this);
    }

    /** A positional sound at {@link #position()} to everyone rendering the world. */
    public void sound(@NotNull SoundEvent sound, @NotNull Sound.Source src, float volume, float pitch) {
        emit(FxAudience.WATCHERS, FxEffect.sound(sound, src, volume, pitch));
    }

    /**
     * A positional sound to the {@code source}'s viewers but NOT the source itself - the sound analogue of
     * {@link #hitAnimation}: the doer's own client predicts it, so echoing it would double it.
     */
    public void viewerSound(@NotNull SoundEvent sound, @NotNull Sound.Source src, float volume, float pitch) {
        emit(FxAudience.VIEWERS, FxEffect.sound(sound, src, volume, pitch));
    }

    /**
     * {@link #viewerSound} plus the {@code source} when it is a LEGACY player: modern clients predict
     * their own world sounds locally; 1.8's local sound sinks are empty stubs, so the doer needs the packet.
     */
    public void predictedSound(@NotNull SoundEvent sound, @NotNull Sound.Source src, float volume, float pitch) {
        emit(FxAudience.PREDICTED, FxEffect.sound(sound, src, volume, pitch));
    }

    /** A sound to the {@code source} entity ONLY, anchored on it, if it's a player (the arrow hit-marker "ding"). */
    public void sourceSound(@NotNull SoundEvent sound, @NotNull Sound.Source src, float volume, float pitch) {
        emit(FxAudience.SOURCE, FxEffect.sound(sound, src, volume, pitch));
    }

    /**
     * The sound to everyone rendering the world, anchored on each LISTENER so distance never attenuates it.
     * One shared seed, so variant-picking sounds pick the same variant for everyone.
     */
    public void globalSound(@NotNull SoundEvent sound, @NotNull Sound.Source src, float volume, float pitch) {
        emit(FxAudience.atListener(FxAudience.WATCHERS), FxEffect.sound(sound, src, volume, pitch));
    }

    /** A particle burst at {@link #position()} to the shard audience. */
    public void particle(@NotNull Particle particle, int count, double offsetX, double offsetY, double offsetZ, float speed) {
        world.broadcast(new ParticlePacket(particle, position.x(), position.y(), position.z(),
                (float) offsetX, (float) offsetY, (float) offsetZ, speed, count));
    }

    /** An entity animation on the {@code source} to its viewers + itself; no-op without a source. */
    public void entityAnimation(EntityAnimationPacket.@NotNull Animation animation) {
        if (source != null) source.sendPacketToViewersAndSelf(new EntityAnimationPacket(source.getEntityId(), animation));
    }

    /**
     * A hit animation on the {@code target} (crit / magic-crit sparkle) to everyone tracking the {@code source} but NOT
     * the source itself: both the 1.8 and 26.1 client predict their own crit locally ({@code EntityPlayerSP.onCriticalHit}
     * / {@code LocalPlayer.crit}), so echoing it back doubles the particles. Vanilla sends to self anyway and thus
     * doubles; polyp doesn't. Universal - not version-gated.
     */
    public void hitAnimation(EntityAnimationPacket.@NotNull Animation animation) {
        if (source != null && target != null) {
            source.sendPacketToViewers(new EntityAnimationPacket(target.getEntityId(), animation));
        }
    }

    /**
     * {@link #hitAnimation} including the source itself: a server-filled (fake) hit never registered on the attacker's
     * client, so it predicts nothing and must be sent the sparkle too.
     */
    public void hitAnimationAll(EntityAnimationPacket.@NotNull Animation animation) {
        if (source != null && target != null) {
            source.sendPacketToViewersAndSelf(new EntityAnimationPacket(target.getEntityId(), animation));
        }
    }
}
