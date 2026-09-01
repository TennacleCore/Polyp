package io.github.term4.polyp.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One entry of a generated knob table: a config field made addressable by name, for {@link PathEdits}.
 * {@code get} reads the {@link FieldValue} off a config instance; {@code set} writes one into that config's
 * builder. The tables are generated next to the classes they touch, so their casts hold by construction.
 *
 * @param valueType the field's value class, or {@code null} for generic types (code-only - no path writes)
 */
public record ConfigKnob(String name, @Nullable Class<?> valueType,
                         Function<Object, @Nullable Object> get, BiConsumer<Object, Object> set) {
}
