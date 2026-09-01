package io.github.term4.polyp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry is global: a duplicate is an error, a removal is explicit, and reading it never finds it empty. */
class FieldFnsTest {

    interface Probe { String name(); }

    @Test
    void aDuplicateNameIsAnErrorNotAnOverwrite() {
        FieldFns.register(Probe.class, "one", "first", args -> () -> "first");
        try {
            String reason = assertThrows(IllegalStateException.class,
                    () -> FieldFns.register(Probe.class, "one", "second", args -> () -> "second")).getMessage();
            assertTrue(reason.contains("already has 'one'"), reason);
            assertEquals("first", FieldFns.parse(Probe.class, "one", "test").name(), "the original stands");
        } finally {
            assertTrue(FieldFns.unregister(Probe.class, "one"));
            assertFalse(FieldFns.unregister(Probe.class, "one"), "gone means gone");
        }
    }

    @Test
    void theShippedVocabularyIsThereOnFirstTouch() {
        // no PathEdits access first: the bootstrap seam, not class-load luck, fills the registry
        assertTrue(FieldFns.names(io.github.term4.polyp.mechanics.explosion.DamageModel.class).contains("curve"));
        assertTrue(FieldFns.supports(io.github.term4.polyp.fx.FxHandler.class));
    }
}
