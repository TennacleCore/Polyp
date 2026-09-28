package io.github.term4.polyp.mechanics.mobs;

/** How a mob's hit on a player scales with the difficulty, before armor. */
@FunctionalInterface
public interface DifficultyScaling {

    float scale(Difficulty difficulty, float amount);

    /** 1.8 EntityPlayer.attackEntityFrom. */
    DifficultyScaling LEGACY = (d, a) -> switch (d) {
        case PEACEFUL -> 0f;
        case EASY -> a / 2f + 1f;
        case NORMAL -> a;
        case HARD -> a * 3f / 2f;
    };

    /** 26.1 Player.hurtServer: easy never raises a hit. */
    DifficultyScaling MODERN = (d, a) -> switch (d) {
        case PEACEFUL -> 0f;
        case EASY -> Math.min(a / 2f + 1f, a);
        case NORMAL -> a;
        case HARD -> a * 3f / 2f;
    };
}
