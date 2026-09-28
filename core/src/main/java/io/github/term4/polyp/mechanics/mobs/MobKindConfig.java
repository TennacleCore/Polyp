package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.mobs.MobsConfigResolver.MobContext;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One mob kind's knobs. The dragon reads the {@code breaks}..{@code roamHeight} group, the silverfish
 * {@code hidesInBlocks}/{@code callsFromBlocks}; the rest is shared.
 */
@GenerateBuilder
public final class MobKindConfig extends Config<MobContext, MobKindConfig> {

    public final @Nullable FieldValue<MobContext, Double> width;
    public final @Nullable FieldValue<MobContext, Double> height;
    public final @Nullable FieldValue<MobContext, Double> eyeHeight;
    public final @Nullable FieldValue<MobContext, Double> maxHealth;
    public final @Nullable FieldValue<MobContext, Double> speed;
    public final @Nullable FieldValue<MobContext, Double> followRange;
    public final @Nullable FieldValue<MobContext, Double> knockbackResistance;
    public final @Nullable FieldValue<MobContext, Double> stepHeight;
    /** The swing's base damage; rolled per hit (the golem's 7 + rand(15)). */
    public final @Nullable FieldValue<MobContext, Float> attackDamage;
    public final @Nullable FieldValue<MobContext, Integer> attackInterval;
    public final @Nullable FieldValue<MobContext, MeleeReach> reach;
    /** Extra upward motion on a landed swing (the golem's 0.4). */
    public final @Nullable FieldValue<MobContext, Double> attackLift;
    public final @Nullable FieldValue<MobContext, MobSound> attackSound;
    public final @Nullable FieldValue<MobContext, Integer> targetChance;
    public final @Nullable FieldValue<MobContext, Boolean> targetSight;
    public final @Nullable FieldValue<MobContext, Boolean> targetNearbyOnly;
    /** Skips the invisibility range cut when picking players. */
    public final @Nullable FieldValue<MobContext, Boolean> seesInvisible;
    /** Who the nearest-target scan may pick; the dragon's flight targets too. */
    public final @Nullable FieldValue<MobContext, Predicate<LivingEntity>> targetSelector;
    /** Who this mob may hurt at all (1.8 canAttackClass); the dragon's bite and wing victims too. */
    public final @Nullable FieldValue<MobContext, Predicate<LivingEntity>> attackable;
    /** Counts as a monster for other mobs' target scans. */
    public final @Nullable FieldValue<MobContext, Boolean> hostile;
    public final @Nullable FieldValue<MobContext, Boolean> fallDamage;
    public final @Nullable FieldValue<MobContext, Boolean> avoidsWater;
    public final @Nullable FieldValue<MobContext, MobSound> ambientSound;
    public final @Nullable FieldValue<MobContext, MobSound> hurtSound;
    public final @Nullable FieldValue<MobContext, MobSound> deathSound;
    public final @Nullable FieldValue<MobContext, MobSound> stepSound;
    public final @Nullable FieldValue<MobContext, Boolean> hidesInBlocks;
    public final @Nullable FieldValue<MobContext, Boolean> callsFromBlocks;
    public final @Nullable FieldValue<MobContext, Predicate<Block>> breaks;
    /** A hit on any part but the head, from the hit's amount. */
    public final @Nullable FieldValue<MobContext, PartDamage> partDamage;
    /** Whether a weapon's enchant bonus counts against the dragon. */
    public final @Nullable FieldValue<MobContext, Boolean> enchantBonus;
    public final @Nullable FieldValue<MobContext, Double> wingPush;
    public final @Nullable FieldValue<MobContext, Double> wingLift;
    public final @Nullable FieldValue<MobContext, Float> biteDamage;
    public final @Nullable FieldValue<MobContext, Double> roamRadius;
    public final @Nullable FieldValue<MobContext, Double> roamMinY;
    public final @Nullable FieldValue<MobContext, Double> roamHeight;

    private MobKindConfig(Builder b) {
        super(b.subConfig);
        this.width = b.width;
        this.height = b.height;
        this.eyeHeight = b.eyeHeight;
        this.maxHealth = b.maxHealth;
        this.speed = b.speed;
        this.followRange = b.followRange;
        this.knockbackResistance = b.knockbackResistance;
        this.stepHeight = b.stepHeight;
        this.attackDamage = b.attackDamage;
        this.attackInterval = b.attackInterval;
        this.reach = b.reach;
        this.attackLift = b.attackLift;
        this.attackSound = b.attackSound;
        this.targetChance = b.targetChance;
        this.targetSight = b.targetSight;
        this.targetNearbyOnly = b.targetNearbyOnly;
        this.seesInvisible = b.seesInvisible;
        this.targetSelector = b.targetSelector;
        this.attackable = b.attackable;
        this.hostile = b.hostile;
        this.fallDamage = b.fallDamage;
        this.avoidsWater = b.avoidsWater;
        this.ambientSound = b.ambientSound;
        this.hurtSound = b.hurtSound;
        this.deathSound = b.deathSound;
        this.stepSound = b.stepSound;
        this.hidesInBlocks = b.hidesInBlocks;
        this.callsFromBlocks = b.callsFromBlocks;
        this.breaks = b.breaks;
        this.partDamage = b.partDamage;
        this.enchantBonus = b.enchantBonus;
        this.wingPush = b.wingPush;
        this.wingLift = b.wingLift;
        this.biteDamage = b.biteDamage;
        this.roamRadius = b.roamRadius;
        this.roamMinY = b.roamMinY;
        this.roamHeight = b.roamHeight;
    }

    @Override
    public MobKindConfig fromBase(MobKindConfig base) {
        Builder b = new Builder();
        b.subConfig(subConfig != null ? subConfig : base.subConfig);
        b.mergeKnobs(this, base);
        return b.build();
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends MobKindConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }

        private Function<MobContext, MobKindConfig> subConfig;

        Builder() {}

        Builder(MobKindConfig c) {
            super(c);
            subConfig = c.subConfig;
        }

        public Builder subConfig(Function<MobContext, MobKindConfig> fn) { subConfig = fn; return this; }

        public MobKindConfig build() { return new MobKindConfig(this); }
    }
}
