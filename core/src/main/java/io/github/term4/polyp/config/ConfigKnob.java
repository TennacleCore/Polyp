package io.github.term4.polyp.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One entry of a generated knob table: a config field made addressable by name, for {@link PathEdits}.
 * {@code set} writes a value into that config's builder - a {@link FieldValue} when {@code fieldValued}
 * (a {@code @GenerateBuilder} config), else the plain decoded value (a {@code @GenerateKnobs} one).
 * The tables are generated next to the classes they touch, so their casts hold by construction.
 *
 * @param valueType the field's value class, or {@code null} for generic types (code-only - no path writes)
 * @param get       reads the field off a config instance; {@code null} when the table does not expose one
 */
public record ConfigKnob(String name, @Nullable Class<?> valueType, boolean fieldValued,
                         @Nullable Function<Object, @Nullable Object> get,
                         BiConsumer<Object, Object> set) {
}
