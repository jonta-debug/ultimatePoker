import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** A short visual-only pulse around the winning player badge. */
public final class WinnerHighlightAnimation implements TableAnimation {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final Rectangle badgeBounds;
    private final long durationNanos;
    private long startTimeNanos;

    public WinnerHighlightAnimation(Rectangle badgeBounds, long durationMillis) {
        if (badgeBounds == null) {
            throw new IllegalArgumentException("Winner badge bounds must not be null");
        }
        this.badgeBounds = new Rectangle(badgeBounds);
        this.durationNanos = Math.max(1, durationMillis) * NANOS_PER_MILLISECOND;
    }

    @Override
    public void start(long startTimeNanos) {
        this.startTimeNanos = startTimeNanos;
    }

    @Override
    public void draw(Graphics2D graphics, long nowNanos) {
        double progress = Easing.clamp((double) (nowNanos - startTimeNanos) / durationNanos);
        double wave = 0.5 + 0.5 * Math.sin(progress * Math.PI * 6.0);
        double fade = 1.0 - Easing.easeInOutCubic(Math.max(0.0, (progress - 0.72) / 0.28));
        double strength = wave * fade;
        double expansion = 7 + strength * 9;

        Graphics2D glow = (Graphics2D) graphics.create();
        glow.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D shape = new RoundRectangle2D.Double(
            badgeBounds.x - expansion,
            badgeBounds.y - expansion,
            badgeBounds.width + expansion * 2,
            badgeBounds.height + expansion * 2,
            30, 30);
        glow.setColor(new Color(250, 207, 82, (int) Math.round(25 + 70 * strength)));
        glow.fill(shape);
        glow.setColor(new Color(255, 225, 133, (int) Math.round(70 + 130 * strength)));
        glow.setStroke(new BasicStroke((float) (1.5 + strength * 2.5)));
        glow.draw(shape);
        glow.dispose();
    }

    @Override
    public boolean isFinished(long nowNanos) {
        return nowNanos - startTimeNanos >= durationNanos;
    }

    @Override
    public AnimationSlot getTargetSlot() {
        return null;
    }
}
