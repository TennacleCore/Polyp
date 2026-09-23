package io.github.term4.polyp.platform.compatibility;

import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.Entity;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.codegen.GenerateBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Immutable cross-version compatibility config: per-scope knobs that present consistent (typically 1.8-style)
 * mechanics to mixed-version clients. Scoped via {@code MechanicsProfile.compat}, pushed to {@code OptimizedPlayer}
 * by {@code PlayerConfigApplier}. Plain values (platform knobs, not per-hit); unset ({@code null}) = unmanaged.
 */
@GenerateBuilder
public final class CompatConfig {

    /** Poses the server forces back to {@code STANDING}; the gameplay half is {@link #restrictMovement}. Empty = none disabled. */
    public final @Nullable FieldValue<CompatContext, Set<EntityPose>> disabledPoses;
    /**
     * Suppress modern swim-posing by gating sprint client-side while in water (food &le; 6 and blindness are the only
     * wire-drivable sprint gates). {@code FOOD} clamps outgoing food to 6 (3-shank HUD, server food untouched);
     * {@code BLINDNESS} maintains a hidden effect (steady near fog while underwater). Non-Animatium modern clients
     * only, and never creative/spectator: mayfly bypasses the client's food gate, so {@code FOOD} can't hold there.
     */
    public final @Nullable FieldValue<CompatContext, SwimSuppression> suppressSwim;
    /**
     * Effect duration for {@code suppressSwim = BLINDNESS}, re-sent every tick; {@code null} = 60. The client's fog
     * saturates at 20 ticks - below that it sits in the fade region and the per-tick refresh makes it strobe.
     */
    public final @Nullable FieldValue<CompatContext, Integer> swimBlindnessTicks;
    /** Reject a move that newly places the SERVER hitbox in block collision - a client rendering itself crawling can't traverse a gap its server hitbox can't fit. */
    public final @Nullable FieldValue<CompatContext, Boolean> restrictMovement;
    /** Server box stays at standing dimensions (no crouch shrink) + 1.8 eye heights (1.54 sneaking); the client still renders its own pose. */
    public final @Nullable FieldValue<CompatContext, Boolean> legacyHitbox;
    /** {@code attack_range.hitbox_margin} (1.8 = {@code 0.1f}, modern 0.3). Held-item only - bare-hand hardcodes 0
     *  client-side. The server pads its melee box by this for every client above 1.8; the STAMP that tells the
     *  client reaches 1.21.11 and up alone, since that is where the component exists. */
    public final @Nullable FieldValue<CompatContext, Float> attackHitboxMargin;
    /** F-swap + offhand-slot clicks cancelled; no effect on 1.8 clients. */
    public final @Nullable FieldValue<CompatContext, Boolean> disableOffhand;
    /** Cancel the sprint speed boost while sneaking (1.8 can't sprint-sneak). */
    public final @Nullable FieldValue<CompatContext, Boolean> restrictSprintSneak;
    /** Cancel the sprint speed boost while using an item (1.8 can't sprint-use). */
    public final @Nullable FieldValue<CompatContext, Boolean> restrictSprintUse;
    /** Clear the server's sprint flag on every arrival (a non-first spawn); off = it follows the client's entity,
     *  cleared only where a respawn packet recreates it. */
    public final @Nullable FieldValue<CompatContext, Boolean> resetSprintOnSpawn;
    /** Cancel the sprint speed boost while in water (caps modern fast-swim toward 1.8). */
    public final @Nullable FieldValue<CompatContext, Boolean> restrictSwimSpeed;
    /** Horizontal divisor for the {@link #restrictSwimSpeed} dampen (higher = slower); {@code null} = 1.25. */
    public final @Nullable FieldValue<CompatContext, Double> swimFactor;
    /** Vertical divisor for the {@link #restrictSwimSpeed} dampen (stronger - holding space re-adds swim-up); {@code null} = 3.0. */
    public final @Nullable FieldValue<CompatContext, Double> swimVerticalFactor;
    /** {@code attack_range} reach stamped alongside {@link #attackHitboxMargin} (1.8 = 3 blocks); {@code null} = 3. */
    public final @Nullable FieldValue<CompatContext, Float> attackReach;
    /** Max blocks from the SERVER eye to a placement's clicked point; farther is cancelled (modern sneak-bridge over-reach). Pair with {@link #legacyHitbox}. */
    public final @Nullable FieldValue<CompatContext, Double> blockPlaceReach;
    /** No placing against an air cell. Enforced client-side (Animatium) AND server-side ({@code CompatPlacement}, any client). */
    public final @Nullable FieldValue<CompatContext, Boolean> oldPlacement;
    /** A LEGACY placer lands stairs in their own body, as a 1.8 client on Paper does; {@code null} = on.
     *  {@code false} = the hypixel refusal: they bounce. Nothing else lands there either way. */
    public final @Nullable FieldValue<CompatContext, Boolean> legacySelfPlace;
    /** Remove the modern attack cooldown + crosshair indicator (huge {@code ATTACK_SPEED}). Server-side, any client. */
    public final @Nullable FieldValue<CompatContext, Boolean> removeAttackCooldown;
    /** No arm-swing when a modern client throws a projectile: its inventory VIEW shows a non-usable reskin; the server item stays the real snowball/egg/pearl. */
    public final @Nullable FieldValue<CompatContext, Boolean> suppressThrowSwing;
    /** Server-side ray fill for a modern client's bare-fist swings: {@link #attackHitboxMargin} only rides items, so an empty hand still picks with margin 0. */
    public final @Nullable FieldValue<CompatContext, Boolean> fistRayHits;
    /** {@code blocks_attacks} stamped on swords in a modern client's VIEW, so a right-click hold shows the native block animation; the server's blocking system stays damage-authoritative. */
    public final @Nullable FieldValue<CompatContext, Boolean> swordBlockingPose;
    /** {@code use_cooldown} stripped from a modern client's item VIEW - the client self-applies it (ender pearl 1s) even without server packets. */
    public final @Nullable FieldValue<CompatContext, Boolean> removeUseCooldowns;

    /** 1.8 water/lava movement (drag/gravity, no swim sprint/buoyancy, no lava current). */
    public final @Nullable FieldValue<CompatContext, Boolean> legacyFluids;
    /** {@code glider} stripped from every modern client's item VIEW, so the client never attempts a glide (Animatium also disables natively - harmless doubled). */
    public final @Nullable FieldValue<CompatContext, Boolean> disableElytraFlight;
    /** Opt-out for the hook prediction escort (default ON): on a silent hook wire, a viewer that can't predict the hook's water physics gets a dense server wire through the water window. */
    public final @Nullable FieldValue<CompatContext, Boolean> hookPredictionEscort;
    /** 1.8 creative/spectator flight (sneaking while flying slows horizontal). */
    public final @Nullable FieldValue<CompatContext, Boolean> oldFlight;
    /** Allow starting an item use while mining (modern MC blocks use-item during destroy; 1.8 doesn't). */
    public final @Nullable FieldValue<CompatContext, Boolean> leftClickItemUsage;
    /** No auto-crouch under a low ceiling (1.8 sneak is shift-only). */
    public final @Nullable FieldValue<CompatContext, Boolean> disableAutoSneak;
    /** Bundle for the four physics aspects below: {@code true} enables all; each per-aspect knob overrides it. */
    public final @Nullable FieldValue<CompatContext, Boolean> oldPhysics;
    /** 1.8 parkour momentum threshold; {@code null} follows {@link #oldPhysics}. */
    public final @Nullable FieldValue<CompatContext, Boolean> oldMomentum;
    /** 1.8 beds don't bounce; {@code null} follows {@link #oldPhysics}. */
    public final @Nullable FieldValue<CompatContext, Boolean> disableBedBounce;
    /** Honey acts like a plain block (no slide/slowdown); {@code null} follows {@link #oldPhysics}. */
    public final @Nullable FieldValue<CompatContext, Boolean> disableHoneyPhysics;
    /** No bubble-column push; {@code null} follows {@link #oldPhysics}. */
    public final @Nullable FieldValue<CompatContext, Boolean> disableBubbleColumn;
    /** No entity-collision shove: a shared no-push TEAM for any modern client + the Animatium native feature. An app running its own teams must disable this and set collisionRule NEVER on them. */
    public final @Nullable FieldValue<CompatContext, Boolean> disableEntityPush;
    /** Byte-exact 1.8 velocity (3 shorts) for clients advertising {@code SHORTS_VELOCITY}; never sent to clients without the decoder. */
    public final @Nullable FieldValue<CompatContext, Boolean> nativeShortVelocity;
    /** Explicit override for the {@link AnimatiumFeature} set sent; {@code null} = derive from the knobs above. */
    public final @Nullable FieldValue<CompatContext, Set<AnimatiumFeature>> animatiumFeatures;
    /** Chat-message the player each time their {@code set_server_features} payload is sent (mod-dev debugging). */
    public final @Nullable FieldValue<CompatContext, Boolean> animatiumDebug;

    private CompatConfig(Builder b) {
        disabledPoses = b.disabledPoses;
        suppressSwim = b.suppressSwim;
        swimBlindnessTicks = b.swimBlindnessTicks;
        restrictMovement = b.restrictMovement;
        legacyHitbox = b.legacyHitbox;
        attackHitboxMargin = b.attackHitboxMargin;
        disableOffhand = b.disableOffhand;
        restrictSprintSneak = b.restrictSprintSneak;
        restrictSprintUse = b.restrictSprintUse;
        resetSprintOnSpawn = b.resetSprintOnSpawn;
        restrictSwimSpeed = b.restrictSwimSpeed;
        swimFactor = b.swimFactor;
        swimVerticalFactor = b.swimVerticalFactor;
        attackReach = b.attackReach;
        blockPlaceReach = b.blockPlaceReach;
        legacyFluids = b.legacyFluids;
        disableElytraFlight = b.disableElytraFlight;
        hookPredictionEscort = b.hookPredictionEscort;
        oldFlight = b.oldFlight;
        leftClickItemUsage = b.leftClickItemUsage;
        disableAutoSneak = b.disableAutoSneak;
        oldPhysics = b.oldPhysics;
        oldMomentum = b.oldMomentum;
        disableBedBounce = b.disableBedBounce;
        disableHoneyPhysics = b.disableHoneyPhysics;
        disableBubbleColumn = b.disableBubbleColumn;
        disableEntityPush = b.disableEntityPush;
        oldPlacement = b.oldPlacement;
        legacySelfPlace = b.legacySelfPlace;
        removeAttackCooldown = b.removeAttackCooldown;
        suppressThrowSwing = b.suppressThrowSwing;
        fistRayHits = b.fistRayHits;
        swordBlockingPose = b.swordBlockingPose;
        removeUseCooldowns = b.removeUseCooldowns;
        nativeShortVelocity = b.nativeShortVelocity;
        animatiumFeatures = b.animatiumFeatures;
        animatiumDebug = b.animatiumDebug;
    }

    public enum SwimSuppression { FOOD, BLINDNESS }

    /** What a compat knob resolves against: the player the policy applies to. */
    public record CompatContext(@Nullable Entity subject) implements SubjectContext {}

    public @Nullable Set<EntityPose> disabledPoses(CompatContext ctx) { return FieldValue.resolve(disabledPoses, ctx); }
    public @Nullable SwimSuppression suppressSwim(CompatContext ctx) { return FieldValue.resolve(suppressSwim, ctx); }
    public @Nullable Integer swimBlindnessTicks(CompatContext ctx) { return FieldValue.resolve(swimBlindnessTicks, ctx); }
    public @Nullable Boolean restrictMovement(CompatContext ctx) { return FieldValue.resolve(restrictMovement, ctx); }
    public @Nullable Boolean legacyHitbox(CompatContext ctx) { return FieldValue.resolve(legacyHitbox, ctx); }
    public @Nullable Float attackHitboxMargin(CompatContext ctx) { return FieldValue.resolve(attackHitboxMargin, ctx); }
    public @Nullable Boolean disableOffhand(CompatContext ctx) { return FieldValue.resolve(disableOffhand, ctx); }
    public @Nullable Boolean restrictSprintSneak(CompatContext ctx) { return FieldValue.resolve(restrictSprintSneak, ctx); }
    public @Nullable Boolean restrictSprintUse(CompatContext ctx) { return FieldValue.resolve(restrictSprintUse, ctx); }
    public @Nullable Boolean resetSprintOnSpawn(CompatContext ctx) { return FieldValue.resolve(resetSprintOnSpawn, ctx); }
    public @Nullable Boolean restrictSwimSpeed(CompatContext ctx) { return FieldValue.resolve(restrictSwimSpeed, ctx); }
    public @Nullable Double swimFactor(CompatContext ctx) { return FieldValue.resolve(swimFactor, ctx); }
    public @Nullable Double swimVerticalFactor(CompatContext ctx) { return FieldValue.resolve(swimVerticalFactor, ctx); }
    public @Nullable Float attackReach(CompatContext ctx) { return FieldValue.resolve(attackReach, ctx); }
    public @Nullable Double blockPlaceReach(CompatContext ctx) { return FieldValue.resolve(blockPlaceReach, ctx); }
    public @Nullable Boolean oldPlacement(CompatContext ctx) { return FieldValue.resolve(oldPlacement, ctx); }
    public @Nullable Boolean legacySelfPlace(CompatContext ctx) { return FieldValue.resolve(legacySelfPlace, ctx); }
    public @Nullable Boolean removeAttackCooldown(CompatContext ctx) { return FieldValue.resolve(removeAttackCooldown, ctx); }
    public @Nullable Boolean suppressThrowSwing(CompatContext ctx) { return FieldValue.resolve(suppressThrowSwing, ctx); }
    public @Nullable Boolean fistRayHits(CompatContext ctx) { return FieldValue.resolve(fistRayHits, ctx); }
    public @Nullable Boolean swordBlockingPose(CompatContext ctx) { return FieldValue.resolve(swordBlockingPose, ctx); }
    public @Nullable Boolean removeUseCooldowns(CompatContext ctx) { return FieldValue.resolve(removeUseCooldowns, ctx); }
    public @Nullable Boolean legacyFluids(CompatContext ctx) { return FieldValue.resolve(legacyFluids, ctx); }
    public @Nullable Boolean disableElytraFlight(CompatContext ctx) { return FieldValue.resolve(disableElytraFlight, ctx); }
    public @Nullable Boolean hookPredictionEscort(CompatContext ctx) { return FieldValue.resolve(hookPredictionEscort, ctx); }
    public @Nullable Boolean oldFlight(CompatContext ctx) { return FieldValue.resolve(oldFlight, ctx); }
    public @Nullable Boolean leftClickItemUsage(CompatContext ctx) { return FieldValue.resolve(leftClickItemUsage, ctx); }
    public @Nullable Boolean disableAutoSneak(CompatContext ctx) { return FieldValue.resolve(disableAutoSneak, ctx); }
    public @Nullable Boolean oldPhysics(CompatContext ctx) { return FieldValue.resolve(oldPhysics, ctx); }
    public @Nullable Boolean oldMomentum(CompatContext ctx) { return FieldValue.resolve(oldMomentum, ctx); }
    public @Nullable Boolean disableBedBounce(CompatContext ctx) { return FieldValue.resolve(disableBedBounce, ctx); }
    public @Nullable Boolean disableHoneyPhysics(CompatContext ctx) { return FieldValue.resolve(disableHoneyPhysics, ctx); }
    public @Nullable Boolean disableBubbleColumn(CompatContext ctx) { return FieldValue.resolve(disableBubbleColumn, ctx); }
    public @Nullable Boolean disableEntityPush(CompatContext ctx) { return FieldValue.resolve(disableEntityPush, ctx); }
    public @Nullable Boolean nativeShortVelocity(CompatContext ctx) { return FieldValue.resolve(nativeShortVelocity, ctx); }
    public @Nullable Set<AnimatiumFeature> animatiumFeatures(CompatContext ctx) { return FieldValue.resolve(animatiumFeatures, ctx); }
    public @Nullable Boolean animatiumDebug(CompatContext ctx) { return FieldValue.resolve(animatiumDebug, ctx); }


    /**
     * This config as it applies to a client speaking {@code protocol}: a knob whose {@link CompatCatalog} range does
     * not cover it reads unset. Most knobs are ANY - they are an era's mechanics, not a client's rendering.
     */
    public CompatConfig scopedTo(int protocol) {
        Builder b = null;
        for (var entry : io.github.term4.polyp.platform.compatibility.CompatConfigBuilderBase.KNOBS.entrySet()) {
            if (entry.getValue().get().apply(this) == null || CompatCatalog.applies(entry.getKey()).covers(protocol)) continue;
            if (b == null) b = toBuilder();
            entry.getValue().set().accept(b, null);
        }
        return b == null ? this : b.build();
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return builder(null); }
    public static Builder builder(@Nullable CompatConfig base) { return base != null ? new Builder(base) : new Builder(); }

    /** Sets are defensively copied. */
    public static final class Builder extends CompatConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }


        Builder() {}

        Builder(CompatConfig c) { super(c); }

        public Builder disabledPoses(EntityPose... poses) { return disabledPoses(Set.of(poses)); }
        public Builder animatiumFeatures(AnimatiumFeature... features) { return animatiumFeatures(Set.of(features)); }

        public CompatConfig build() { return new CompatConfig(this); }
    }
}
