package io.github.term4.polyp.mechanics.mobs.path;

/** 26.1 PathType: what a cell is to a walker, with its cost (negative = never). */
public enum PathType {
    BLOCKED(-1.0f),
    OPEN(0.0f),
    WALKABLE(0.0f),
    WALKABLE_DOOR(0.0f),
    TRAPDOOR(0.0f),
    POWDER_SNOW(-1.0f),
    ON_TOP_OF_POWDER_SNOW(0.0f),
    FENCE(-1.0f),
    LAVA(-1.0f),
    WATER(8.0f),
    WATER_BORDER(8.0f),
    RAIL(0.0f),
    UNPASSABLE_RAIL(-1.0f),
    FIRE_IN_NEIGHBOR(8.0f),
    FIRE(16.0f),
    DAMAGING_IN_NEIGHBOR(8.0f),
    DAMAGING(-1.0f),
    DOOR_OPEN(0.0f),
    DOOR_WOOD_CLOSED(-1.0f),
    DOOR_IRON_CLOSED(-1.0f),
    BREACH(4.0f),
    LEAVES(-1.0f),
    STICKY_HONEY(8.0f),
    COCOA(0.0f),
    DAMAGE_CAUTIOUS(0.0f),
    ON_TOP_OF_TRAPDOOR(0.0f),
    BIG_MOBS_CLOSE_TO_DANGER(4.0f);

    private final float malus;

    PathType(float malus) {
        this.malus = malus;
    }

    public float malus() { return malus; }
}
