package io.github.term4.polyp.mechanics.mobs;

/** The world difficulty Minestom has no notion of; a mob's damage on players and its path courage read it. */
public enum Difficulty {
    PEACEFUL, EASY, NORMAL, HARD;

    public int id() { return ordinal(); }
}
