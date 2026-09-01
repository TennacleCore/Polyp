package io.github.term4.polyp.codegen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Emits {@code <Config>Knobs.KNOBS} for a config whose fields are PLAIN values rather than
 * {@code FieldValue}s - the table {@code PathEdits} needs to address a field by name, and nothing else.
 * Unlike {@link GenerateBuilder} it generates no builder and changes no runtime shape: these configs
 * resolve without a context, and forcing {@code FieldValue} on them would be uniformity for its own sake.
 *
 * <p>Contract: every addressable field needs a builder setter of the SAME NAME taking that field's type
 * ({@code enabled} -> {@code Builder enabled(Boolean)}), which is already the convention here. Fields with
 * no matching setter are skipped, so a map or a computed member simply stays code-only.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateKnobs {
}
