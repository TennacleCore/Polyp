package io.github.term4.polyp.mechanics.mobs;

import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/** A mob's own sound; the pitch is rolled per play (1.8 getSoundPitch). */
public record MobSound(@NotNull SoundEvent sound, float volume, float pitch) {

    public static MobSound of(@NotNull SoundEvent sound, float volume) {
        return new MobSound(sound, volume, 1.0f);
    }

    public MobSound rolled(Random random) {
        return new MobSound(sound, volume, (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
    }
}
