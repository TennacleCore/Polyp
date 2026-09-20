package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Our pair against 1.8's own, over every way two chests can meet. The oracle is {@code BlockChest} transcribed:
 * the block lands carrying the player's LOOK ({@code getPlacedState}), {@code onPlace} runs
 * {@code checkForSurroundingChests} on the new chest and then its chest neighbour, and {@code postPlace} lands last
 * with the look's OPPOSITE, turning both halves when the join crosses it. Air all round, so the full-block
 * tie-breaks never fire.
 */
class LegacyChestParityTest extends HeadlessServerTest {

    private static final int Y = 66, Z0 = 940;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    // the rule reads around a cell before anything is set in it
    @BeforeAll
    static void loadTheRow() {
        for (int cx = 0; cx <= 16; cx++) instance.loadChunk(cx, Z0 >> 4).join(); // 64 cases, 4 apart
    }

    /** The two facings 1.8 ends with: {@code [new, old]}. */
    private static Direction[] vanilla18(Direction side, Direction old, Direction look) {
        Direction[] state = {look, old};
        state[0] = surrounding(side.opposite(), state[1]); // e(new): its neighbour sits opposite the side
        state[1] = surrounding(side, state[0]);            // e(old): the new one sits on `side`
        Direction placed = look.opposite();
        if (placed.normalX() * side.normalX() + placed.normalZ() * side.normalZ() == 0) {
            state[0] = placed;
            state[1] = placed;
        }
        return state;
    }

    /** {@code BlockChest.e}: a pair along X reads SOUTH unless its partner faces NORTH; along Z, EAST unless WEST. */
    private static Direction surrounding(Direction toPartner, Direction partnerFacing) {
        boolean alongZ = toPartner == Direction.NORTH || toPartner == Direction.SOUTH;
        if (alongZ) return partnerFacing == Direction.WEST ? Direction.WEST : Direction.EAST;
        return partnerFacing == Direction.NORTH ? Direction.NORTH : Direction.SOUTH;
    }

    /** The yaw a player looking {@code look} reports (south 0, west 90, north 180, east 270). */
    static float yawOf(Direction look) {
        return switch (look) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            default -> 270f;
        };
    }

    private static String name(Direction d) {
        return d.name().toLowerCase(Locale.ROOT);
    }

    @Test
    void ourPairReadsAs18() {
        List<String> off = new ArrayList<>();
        int x = 0;
        for (Direction side : HORIZONTAL) {
            for (Direction old : HORIZONTAL) {
                for (Direction look : HORIZONTAL) {
                    // a fresh patch of air per case, so no pair sees another
                    BlockVec at = new BlockVec(x += 4, Y, Z0);
                    BlockVec other = new BlockVec(at.blockX() + side.normalX(), Y, at.blockZ() + side.normalZ());
                    instance.setBlock(other, Block.CHEST.withProperty("facing", name(old)));

                    instance.setBlock(at, LegacyChestPlacement.place(instance, instance, at, Block.CHEST, yawOf(look)));

                    Direction[] want = vanilla18(side, old, look);
                    String got = instance.getBlock(at).getProperty("facing") + "/" + instance.getBlock(other).getProperty("facing");
                    String expected = name(want[0]) + "/" + name(want[1]);
                    if (!expected.equals(got)) {
                        off.add("new " + side + " of a " + old + " chest, looking " + look + ": 1.8 " + expected + ", ours " + got);
                    }
                    instance.setBlock(at, Block.AIR);
                    instance.setBlock(other, Block.AIR);
                }
            }
        }
        assertTrue(off.isEmpty(), off.size() + " of 64 differ:\n" + String.join("\n", off));
    }
}
