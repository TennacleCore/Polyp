package io.github.term4.polyp.tracking.motion;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import org.jetbrains.annotations.Nullable;

/**
 * Knobs for the {@link VelocityRule#simulated(VelocityConfig) simulated} server-tracked velocity, resolved per
 * entity through {@link VelocityContext} - so a scope, or a targeted entry, can change one of them. Unset
 * knobs take the vanilla defaults below.
 */
@GenerateBuilder
public final class VelocityConfig {

    public static final double GRAVITY = 0.08;
    public static final double DRAG_V = 0.98;
    public static final double DRAG_H = 0.91;
    public static final double JUMP_VELOCITY = 0.41999998688697815;
    public static final double ZERO_BELOW = 0.005;
    public static final int DEFAULT_LAUNCH_OFFSET = -1;

    public final @Nullable FieldValue<VelocityContext, Double> seed;
    public final @Nullable FieldValue<VelocityContext, Integer> launchOffset;
    public final @Nullable FieldValue<VelocityContext, Double> zeroBelowX;
    public final @Nullable FieldValue<VelocityContext, Double> zeroBelowY;
    public final @Nullable FieldValue<VelocityContext, Double> zeroBelowZ;
    public final @Nullable FieldValue<VelocityContext, Integer> groundTicks;
    public final @Nullable FieldValue<VelocityContext, Integer> maxAirTicks;
    public final @Nullable FieldValue<VelocityContext, Boolean> entityPush;
    public final @Nullable FieldValue<VelocityContext, Boolean> fluidPhysics;
    public final @Nullable FieldValue<VelocityContext, Boolean> climbPhysics;
    public final @Nullable FieldValue<VelocityContext, Boolean> webPhysics;
    public final @Nullable FieldValue<VelocityContext, Boolean> flowPush;
    public final @Nullable FieldValue<VelocityContext, FluidFlow.Model> flowModel;
    public final @Nullable FieldValue<VelocityContext, Boolean> flowLava;
    public final @Nullable FieldValue<VelocityContext, ClimbModel> climbModel;
    public final @Nullable FieldValue<VelocityContext, Boolean> modernBlockPhysics;
    public final @Nullable FieldValue<VelocityContext, Boolean> motYOnMovePacket;
    public final @Nullable FieldValue<VelocityContext, Double> wireFloorX;
    public final @Nullable FieldValue<VelocityContext, Double> wireFloorY;
    public final @Nullable FieldValue<VelocityContext, Double> wireFloorZ;

    private VelocityConfig(Builder b) {
        seed = b.seed;
        launchOffset = b.launchOffset;
        zeroBelowX = b.zeroBelowX;
        zeroBelowY = b.zeroBelowY;
        zeroBelowZ = b.zeroBelowZ;
        groundTicks = b.groundTicks;
        maxAirTicks = b.maxAirTicks;
        entityPush = b.entityPush;
        fluidPhysics = b.fluidPhysics;
        climbPhysics = b.climbPhysics;
        webPhysics = b.webPhysics;
        flowPush = b.flowPush;
        flowModel = b.flowModel;
        flowLava = b.flowLava;
        climbModel = b.climbModel;
        modernBlockPhysics = b.modernBlockPhysics;
        motYOnMovePacket = b.motYOnMovePacket;
        wireFloorX = b.wireFloorX;
        wireFloorY = b.wireFloorY;
        wireFloorZ = b.wireFloorZ;
    }

    public double seed(VelocityContext ctx) { return FieldValue.resolve(seed, ctx, JUMP_VELOCITY); }
    public int launchOffset(VelocityContext ctx) { return FieldValue.resolve(launchOffset, ctx, DEFAULT_LAUNCH_OFFSET); }
    public double zeroBelowX(VelocityContext ctx) { return FieldValue.resolve(zeroBelowX, ctx, ZERO_BELOW); }
    public double zeroBelowY(VelocityContext ctx) { return FieldValue.resolve(zeroBelowY, ctx, ZERO_BELOW); }
    public double zeroBelowZ(VelocityContext ctx) { return FieldValue.resolve(zeroBelowZ, ctx, ZERO_BELOW); }
    public int groundTicks(VelocityContext ctx) { return FieldValue.resolve(groundTicks, ctx, 1); }
    public @Nullable Integer maxAirTicks(VelocityContext ctx) { return FieldValue.resolve(maxAirTicks, ctx); }
    public boolean entityPush(VelocityContext ctx) { return FieldValue.resolve(entityPush, ctx, true); }
    public boolean fluidPhysics(VelocityContext ctx) { return FieldValue.resolve(fluidPhysics, ctx, true); }
    public boolean climbPhysics(VelocityContext ctx) { return FieldValue.resolve(climbPhysics, ctx, true); }
    public boolean webPhysics(VelocityContext ctx) { return FieldValue.resolve(webPhysics, ctx, true); }
    public boolean flowPush(VelocityContext ctx) { return FieldValue.resolve(flowPush, ctx, true); }
    public FluidFlow.Model flowModel(VelocityContext ctx) { return FieldValue.resolve(flowModel, ctx, FluidFlow.Model.LEGACY); }
    public boolean flowLava(VelocityContext ctx) { return FieldValue.resolve(flowLava, ctx, true); }
    public ClimbModel climbModel(VelocityContext ctx) { return FieldValue.resolve(climbModel, ctx, ClimbModel.LEGACY); }
    public boolean modernBlockPhysics(VelocityContext ctx) { return FieldValue.resolve(modernBlockPhysics, ctx, false); }
    public boolean motYOnMovePacket(VelocityContext ctx) { return FieldValue.resolve(motYOnMovePacket, ctx, false); }
    public @Nullable Double wireFloorX(VelocityContext ctx) { return FieldValue.resolve(wireFloorX, ctx); }
    public @Nullable Double wireFloorY(VelocityContext ctx) { return FieldValue.resolve(wireFloorY, ctx); }
    public @Nullable Double wireFloorZ(VelocityContext ctx) { return FieldValue.resolve(wireFloorZ, ctx); }

    public static VelocityConfig defaults() { return builder().build(); }

    public VelocityConfig fromBase(VelocityConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        return b.build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder extends VelocityConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        Builder() {}
        Builder(VelocityConfig c) { super(c); }

        /** All three axes at once. */
        public Builder zeroBelow(double all) { return zeroBelowX(all).zeroBelowY(all).zeroBelowZ(all); }

        // the attacker self-slowdown lives on AttackConfig.fullHitScale (an attack-time mutation), not here
        public VelocityConfig build() { return new VelocityConfig(this); }
    }
}
