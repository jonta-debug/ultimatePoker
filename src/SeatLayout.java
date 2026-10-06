import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized seat definitions for tables with two to nine players.
 *
 * xRatio/yRatio locate the centre of a player's badge. Card and chip offsets
 * are pixel offsets from that anchor and point toward the table. Keeping this
 * geometry outside PokerTablePanel makes it easy to tune without changing any
 * rendering or game logic.
 */
public final class SeatLayout {
    private static final Map<Integer, List<Seat>> LAYOUTS = createLayouts();

    private SeatLayout() {
    }

    public static List<Seat> forPlayerCount(int playerCount) {
        List<Seat> seats = LAYOUTS.get(playerCount);
        if (seats == null) {
            throw new IllegalArgumentException("Player count must be between 2 and 9: " + playerCount);
        }
        return seats;
    }

    private static Map<Integer, List<Seat>> createLayouts() {
        Map<Integer, List<Seat>> layouts = new LinkedHashMap<>();

        layouts.put(2, seats(
            top(0.50), bottom(0.50)));

        layouts.put(3, seats(
            top(0.50), bottom(0.32), bottom(0.68)));

        layouts.put(4, seats(
            top(0.50), right(0.50), bottom(0.50), left(0.50)));

        layouts.put(5, seats(
            top(0.35), top(0.65), right(0.52), bottom(0.50), left(0.52)));

        layouts.put(6, seats(
            top(0.32), top(0.68), right(0.52),
            bottom(0.68), bottom(0.32), left(0.52)));

        layouts.put(7, seats(
            top(0.27), top(0.50), top(0.73), right(0.52),
            bottom(0.66), bottom(0.34), left(0.52)));

        layouts.put(8, seats(
            top(0.27), top(0.50), top(0.73), right(0.52),
            bottom(0.73), bottom(0.50), bottom(0.27), left(0.52)));

        layouts.put(9, seats(
            top(0.27), top(0.50), top(0.73),
            right(0.36), right(0.65),
            bottom(0.66), bottom(0.34),
            left(0.65), left(0.36)));

        return Collections.unmodifiableMap(layouts);
    }

    private static Seat top(double xRatio) {
        return new Seat(xRatio, 0.09, -45, 38, 62, 51);
    }

    private static Seat bottom(double xRatio) {
        return new Seat(xRatio, 0.91, -45, -116, 62, -68);
    }

    private static Seat left(double yRatio) {
        return new Seat(0.08, yRatio, 84, -43, 84, 20);
    }

    private static Seat right(double yRatio) {
        return new Seat(0.92, yRatio, -174, -43, -106, 20);
    }

    private static List<Seat> seats(Seat... seats) {
        List<Seat> result = new ArrayList<>();
        Collections.addAll(result, seats);
        return Collections.unmodifiableList(result);
    }

    public static final class Seat {
        private final double xRatio;
        private final double yRatio;
        private final int cardOffsetX;
        private final int cardOffsetY;
        private final int chipOffsetX;
        private final int chipOffsetY;

        private Seat(double xRatio, double yRatio, int cardOffsetX, int cardOffsetY,
                     int chipOffsetX, int chipOffsetY) {
            this.xRatio = xRatio;
            this.yRatio = yRatio;
            this.cardOffsetX = cardOffsetX;
            this.cardOffsetY = cardOffsetY;
            this.chipOffsetX = chipOffsetX;
            this.chipOffsetY = chipOffsetY;
        }

        public Point anchor(int width, int height) {
            return new Point((int) Math.round(width * xRatio), (int) Math.round(height * yRatio));
        }

        public double getXRatio() {
            return xRatio;
        }

        public double getYRatio() {
            return yRatio;
        }

        public int getCardOffsetX() {
            return cardOffsetX;
        }

        public int getCardOffsetY() {
            return cardOffsetY;
        }

        public int getChipOffsetX() {
            return chipOffsetX;
        }

        public int getChipOffsetY() {
            return chipOffsetY;
        }
    }
}
