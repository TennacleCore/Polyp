package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import net.minestom.server.coordinate.Point;
import net.minestom.server.utils.Direction;

/** Whether a fluid may reach {@code to} from the context's cell along {@code direction}: down, or one of the four sides. */
@FunctionalInterface
public interface Flow {

    boolean allowed(FluidContext ctx, Direction direction, Point to);

    Flow ANY = (ctx, direction, to) -> true;

    Flow NONE = (ctx, direction, to) -> false;

    static Flow except(Direction shut) {
        return (ctx, direction, to) -> direction != shut;
    }
}
