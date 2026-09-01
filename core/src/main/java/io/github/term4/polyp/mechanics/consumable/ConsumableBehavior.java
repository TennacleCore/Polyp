package io.github.term4.polyp.mechanics.consumable;

import io.github.term4.polyp.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import org.jetbrains.annotations.NotNull;

/**
 * The pre/during/post hooks of a {@link Consumable} - the open seam for custom consume behavior. No-op by default;
 * resolved per consume as a {@link ConsumableTypeConfig} knob.
 */
public interface ConsumableBehavior {

    /** The default. */
    ConsumableBehavior NONE = new ConsumableBehavior() {};

    default void onStart(ConsumableContext ctx) {}

    /** Each tick while consuming, with the ticks left before completion. */
    default void onUsing(ConsumableContext ctx, int ticksRemaining) {}

    /** The system consumes the item afterward. */
    default void onFinish(ConsumableContext ctx) {}

    /** Released before completion. Vanilla = nothing happens. */
    default void onCancel(ConsumableContext ctx) {}

    /**
     * A behavior that just dishes out {@code effects} on finish - what most food IS. A scope changes an item's
     * payload by writing a new list rather than replacing the item, so eat time and edibility stay put.
     */
    static @NotNull ConsumableBehavior payload(@NotNull ConsumeEffect... effects) {
        ConsumeEffect all = ConsumeEffect.all(effects);
        return new ConsumableBehavior() {
            @Override public void onFinish(ConsumableConfigResolver.ConsumableContext ctx) { all.apply(ctx); }
        };
    }

    static void registerFactories() {
        ConsumeEffect.registerFactories();
        io.github.term4.polyp.config.FieldFns.register(ConsumableBehavior.class, "payload(effect...)",
                "dishes out the listed effects on finish", args -> {
                    ConsumeEffect[] out = new ConsumeEffect[args.size()];
                    for (int i = 0; i < out.length; i++) out[i] = args.of(i, ConsumeEffect.class);
                    return payload(out);
                });
    }
}