import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** A visual card movement between a source object and a rendered card slot. */
public final class CardAnimation implements TableAnimation {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final BufferedImage image;
    private final double startX;
    private final double startY;
    private final double targetX;
    private final double targetY;
    private final int width;
    private final int height;
    private final double startScale;
    private final double endScale;
    private final double startRotation;
    private final double endRotation;
    private final float startAlpha;
    private final float endAlpha;
    private final long durationNanos;
    private final long delayNanos;
    private final AnimationSlot targetSlot;
    private long batchStartNanos;

    public CardAnimation(BufferedImage image, Rectangle sourceBounds, Rectangle targetBounds,
                         double startScale, double endScale,
                         double startRotation, double endRotation,
                         float startAlpha, float endAlpha,
                         long durationMillis, long delayMillis,
                         AnimationSlot targetSlot) {
        if (image == null || sourceBounds == null || targetBounds == null) {
            throw new IllegalArgumentException("Card animation image and bounds must not be null");
        }
        this.image = image;
        this.width = targetBounds.width;
        this.height = targetBounds.height;
        this.startX = sourceBounds.getCenterX() - width / 2.0;
        this.startY = sourceBounds.getCenterY() - height / 2.0;
        this.targetX = targetBounds.x;
        this.targetY = targetBounds.y;
        this.startScale = startScale;
        this.endScale = endScale;
        this.startRotation = startRotation;
        this.endRotation = endRotation;
        this.startAlpha = startAlpha;
        this.endAlpha = endAlpha;
        this.durationNanos = Math.max(1, durationMillis) * NANOS_PER_MILLISECOND;
        this.delayNanos = Math.max(0, delayMillis) * NANOS_PER_MILLISECOND;
        this.targetSlot = targetSlot;
    }

    @Override
    public void start(long startTimeNanos) {
        batchStartNanos = startTimeNanos;
    }

    @Override
    public void draw(Graphics2D graphics, long nowNanos) {
        long elapsed = nowNanos - batchStartNanos - delayNanos;
        if (elapsed < 0) {
            return;
        }

        double rawProgress = Easing.clamp((double) elapsed / durationNanos);
        double movementProgress = Easing.easeOutCubic(rawProgress);
        double x = interpolate(startX, targetX, movementProgress);
        double y = interpolate(startY, targetY, movementProgress);
        double scale = interpolate(startScale, endScale, movementProgress);
        double rotation = interpolate(startRotation, endRotation, movementProgress);
        float alpha = (float) interpolate(startAlpha, endAlpha, movementProgress);
        double centerX = x + width / 2.0;
        double centerY = y + height / 2.0;

        Graphics2D shadow = (Graphics2D) graphics.create();
        shadow.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        shadow.translate(centerX + 5, centerY + 7);
        shadow.rotate(rotation);
        shadow.scale(scale, scale);
        shadow.setColor(new Color(0, 0, 0, Math.round(75 * alpha)));
        shadow.fill(new RoundRectangle2D.Double(
            -width / 2.0, -height / 2.0, width, height, 10, 10));
        shadow.dispose();

        Graphics2D cardGraphics = (Graphics2D) graphics.create();
        cardGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        cardGraphics.translate(centerX, centerY);
        cardGraphics.rotate(rotation);
        cardGraphics.scale(scale, scale);
        cardGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
            (float) Easing.clamp(alpha)));
        cardGraphics.drawImage(image, -width / 2, -height / 2, null);
        cardGraphics.dispose();
    }

    @Override
    public boolean isFinished(long nowNanos) {
        return nowNanos - batchStartNanos >= delayNanos + durationNanos;
    }

    @Override
    public AnimationSlot getTargetSlot() {
        return targetSlot;
    }

    private double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
