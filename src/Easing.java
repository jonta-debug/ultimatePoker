/** Stateless easing helpers used by table animations. */
public final class Easing {
    private Easing() {
    }

    public static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static double easeOutCubic(double value) {
        double t = clamp(value);
        double inverse = 1.0 - t;
        return 1.0 - inverse * inverse * inverse;
    }

    public static double easeInOutCubic(double value) {
        double t = clamp(value);
        if (t < 0.5) {
            return 4.0 * t * t * t;
        }
        double inverse = -2.0 * t + 2.0;
        return 1.0 - inverse * inverse * inverse / 2.0;
    }
}
