package io.github.term4.polyp.mechanics.consumable;

import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * <em>Whether</em> a {@link ConsumeEffect} applies - the axis the effect itself deliberately does not carry.
 * Splitting them is what keeps the vocabulary additive: a probability, a missing potion, and a health check
 * are conditions that work with EVERY effect, instead of a {@code chance-effect} and an {@code if-absent}
 * variant per case.
 *
 * @see ConsumeEffect#when(ConsumeCondition, ConsumeEffect)
 */
@FunctionalInterface
public interface ConsumeCondition {

    boolean test(@NotNull ConsumableContext ctx);

    ConsumeCondition ALWAYS = ctx -> true;

    /** The eater already has this potion effect. */
    static @NotNull ConsumeCondition hasEffect(@NotNull PotionEffect id) {
        return ctx -> ctx.user().getActiveEffects().stream()
                .anyMatch(active -> active.potion().effect().equals(id));
    }

    /** A {@code 0..1} probability, rolled per application (vanilla's per-effect chance). */
    static @NotNull ConsumeCondition chance(double probability) {
        return ctx -> ThreadLocalRandom.current().nextFloat() < probability;
    }

    /** The eater is below this many health points. */
    static @NotNull ConsumeCondition healthBelow(double health) {
        return ctx -> ctx.user().getHealth() < health;
    }

    /** The eater is missing at least this many health points. */
    static @NotNull ConsumeCondition missingHealth(double points) {
        return ctx -> ctx.user().getAttributeValue(Attribute.MAX_HEALTH) - ctx.user().getHealth() >= points;
    }

    default @NotNull ConsumeCondition negate() { return ctx -> !test(ctx); }

    static @NotNull ConsumeCondition and(@NotNull ConsumeCondition a, @NotNull ConsumeCondition b) {
        return ctx -> a.test(ctx) && b.test(ctx);
    }

    static @NotNull ConsumeCondition or(@NotNull ConsumeCondition a, @NotNull ConsumeCondition b) {
        return ctx -> a.test(ctx) || b.test(ctx);
    }

    static void registerFactories() {
        FieldFns.register(ConsumeCondition.class, "always", "no condition", args -> ALWAYS);
        FieldFns.register(ConsumeCondition.class, "has-effect(id)", "the eater already has that potion effect",
                args -> hasEffect(potion(args.arity(1), 0)));
        FieldFns.register(ConsumeCondition.class, "chance(probability)", "a 0..1 roll, per application",
                args -> chance(args.arity(1).dbl(0)));
        FieldFns.register(ConsumeCondition.class, "health-below(health)", "the eater is under that much health",
                args -> healthBelow(args.arity(1).dbl(0)));
        FieldFns.register(ConsumeCondition.class, "missing-health(points)", "the eater is down at least that much",
                args -> missingHealth(args.arity(1).dbl(0)));
        FieldFns.register(ConsumeCondition.class, "not(condition)", "the opposite",
                args -> args.arity(1).of(0, ConsumeCondition.class).negate());
        FieldFns.register(ConsumeCondition.class, "and(a, b)", "both hold",
                args -> and(args.arity(2).of(0, ConsumeCondition.class), args.of(1, ConsumeCondition.class)));
        FieldFns.register(ConsumeCondition.class, "or(a, b)", "either holds",
                args -> or(args.arity(2).of(0, ConsumeCondition.class), args.of(1, ConsumeCondition.class)));
    }

    private static PotionEffect potion(FieldFns.Args args, int i) {
        Key key = args.key(i);
        PotionEffect effect = PotionEffect.fromKey(key);
        if (effect == null) throw new IllegalArgumentException("unknown potion effect '" + key.asString() + "'");
        return effect;
    }
}
