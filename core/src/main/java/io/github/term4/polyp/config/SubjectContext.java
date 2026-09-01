package io.github.term4.polyp.config;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A resolution context that is ABOUT an entity - the one that system resolves its scoped profile against,
 * so a {@link FieldValue#targeted targeted} knob can ask "is this player on red?" without knowing which
 * system is asking. Damage, knockback and death are about the victim; attack and projectiles about the doer;
 * an explosion about whoever set it off, if anyone.
 */
public interface SubjectContext {

    @Nullable Entity subject();
}
