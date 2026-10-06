import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.Timer;

/** Runs visual animations on the Swing event thread and requests repaints. */
public final class AnimationManager {
    private static final int FRAME_DELAY_MILLIS = 16;

    private final List<TableAnimation> activeAnimations;
    private final Runnable repaintRequest;
    private final Timer timer;
    private Runnable completionAction;

    public AnimationManager(Runnable repaintRequest) {
        this.repaintRequest = repaintRequest == null ? () -> { } : repaintRequest;
        this.activeAnimations = new ArrayList<>();
        this.timer = new Timer(FRAME_DELAY_MILLIS, event -> update());
        this.timer.setCoalesce(true);
    }

    public void play(List<? extends TableAnimation> animations, Runnable onFinished) {
        clear();
        completionAction = onFinished;

        if (animations == null || animations.isEmpty()) {
            finishBatch();
            return;
        }

        long startTime = System.nanoTime();
        for (TableAnimation animation : animations) {
            if (animation != null) {
                animation.start(startTime);
                activeAnimations.add(animation);
            }
        }

        if (activeAnimations.isEmpty()) {
            finishBatch();
            return;
        }

        timer.start();
        repaintRequest.run();
    }

    public void clear() {
        timer.stop();
        activeAnimations.clear();
        completionAction = null;
        repaintRequest.run();
    }

    public boolean isSlotAnimating(AnimationSlot slot) {
        if (slot == null) {
            return false;
        }
        for (TableAnimation animation : activeAnimations) {
            if (slot.equals(animation.getTargetSlot())) {
                return true;
            }
        }
        return false;
    }

    public void draw(Graphics2D graphics) {
        long now = System.nanoTime();
        for (TableAnimation animation : new ArrayList<>(activeAnimations)) {
            animation.draw(graphics, now);
        }
    }

    private void update() {
        long now = System.nanoTime();
        Iterator<TableAnimation> iterator = activeAnimations.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isFinished(now)) {
                iterator.remove();
            }
        }

        repaintRequest.run();
        if (activeAnimations.isEmpty()) {
            timer.stop();
            finishBatch();
        }
    }

    private void finishBatch() {
        Runnable action = completionAction;
        completionAction = null;
        if (action != null) {
            action.run();
        }
    }
}
