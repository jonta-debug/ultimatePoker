import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Two-dimensional flip illusion that switches from a card back to its face. */
public final class FlipCardAnimation implements TableAnimation {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final BufferedImage backImage;
    private final BufferedImage faceImage;
    private final Rectangle targetBounds;
    private final long durationNanos;
    private final long delayNanos;
    private final AnimationSlot targetSlot;
    private long batchStartNanos;

    public FlipCardAnimation(BufferedImage backImage, BufferedImage faceImage,
                             Rectangle targetBounds, long durationMillis,
                             long delayMillis, AnimationSlot targetSlot) {
        if (backImage == null || faceImage == null || targetBounds == null) {
            throw new IllegalArgumentException("Flip images and target bounds must not be null");
        }
        this.backImage = backImage;
        this.faceImage = faceImage;
        this.targetBounds = new Rectangle(targetBounds);
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
        double progress = elapsed <= 0
            ? 0.0
            : Easing.clamp((double) elapsed / durationNanos);
        boolean showingBack = progress < 0.5;
        double halfProgress = showingBack ? progress * 2.0 : (progress - 0.5) * 2.0;
        double widthScale = showingBack
            ? 1.0 - Easing.easeInOutCubic(halfProgress)
            : Easing.easeInOutCubic(halfProgress);
        widthScale = Math.max(0.04, widthScale);

        BufferedImage image = showingBack ? backImage : faceImage;
        double centerX = targetBounds.getCenterX();
        double centerY = targetBounds.getCenterY();
        int drawnWidth = Math.max(1, (int) Math.round(targetBounds.width * widthScale));
        int x = (int) Math.round(centerX - drawnWidth / 2.0);
        int y = targetBounds.y;

        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setColor(new Color(0, 0, 0, 65));
        g2.fill(new RoundRectangle2D.Double(
            x + 4, y + 5, drawnWidth, targetBounds.height, 10, 10));
        g2.drawImage(image, x, y, drawnWidth, targetBounds.height, null);
        g2.dispose();
    }

    @Override
    public boolean isFinished(long nowNanos) {
        return nowNanos - batchStartNanos >= delayNanos + durationNanos;
    }

    @Override
    public AnimationSlot getTargetSlot() {
        return targetSlot;
    }
}
