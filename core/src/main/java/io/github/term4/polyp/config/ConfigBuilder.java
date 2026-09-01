package io.github.term4.polyp.config;

/**
 * What every config builder can do: build. The one supertype the damage-type builders share, so a subclass
 * config's {@code toBuilder()} can narrow the base's return type instead of colliding with it.
 */
public interface ConfigBuilder<C> {

    C build();
}
