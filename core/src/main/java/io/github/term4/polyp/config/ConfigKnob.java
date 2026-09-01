package io.github.term4.polyp.config;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One entry of a generated knob table ({@code <Config>BuilderBase.KNOBS}): a config's {@link FieldValue}
 * field made addressable by name, for {@link PathEdits}. {@code get} reads the field off a config instance;
 * {@code set} writes a FieldValue into that config's builder (both sides cast internally - the table is
 * generated next to the classes it touches, so the casts hold by construction).
 *
 * @param valueType the field's value class, or {@code null} for generic types (code-only - no path writes)
 */
public record ConfigKnob(String name, @Nullable Class<?> valueType,
                         Function<Object, @Nullable FieldValue<?, ?>> get,
                         BiConsumer<Object, FieldValue<?, ?>> set) {
}
