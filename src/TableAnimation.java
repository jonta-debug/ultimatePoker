import java.awt.Graphics2D;

/** Internal contract for visual-only table animations. */
public interface TableAnimation {
    void start(long startTimeNanos);

    void draw(Graphics2D graphics, long nowNanos);

    boolean isFinished(long nowNanos);

    AnimationSlot getTargetSlot();
}
