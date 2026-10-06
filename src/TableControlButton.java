import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.RoundRectangle2D;

/** Lightweight custom-painted control used by PokerTablePanel. */
final class TableControlButton {
    private final String label;
    private final RoundRectangle2D.Double bounds;
    private Runnable action;
    private boolean hovered;
    private boolean pressed;
    private boolean enabled;

    TableControlButton(String label) {
        this.label = label;
        this.bounds = new RoundRectangle2D.Double();
        this.action = () -> { };
        this.enabled = true;
    }

    void setBounds(double x, double y, double width, double height) {
        bounds.setRoundRect(x, y, width, height, height, height);
    }

    RoundRectangle2D.Double getBounds() {
        return bounds;
    }

    boolean contains(Point point) {
        return enabled && bounds.contains(point);
    }

    boolean setHovered(boolean hovered) {
        boolean nextValue = enabled && hovered;
        if (this.hovered == nextValue) {
            return false;
        }
        this.hovered = nextValue;
        return true;
    }

    boolean setPressed(boolean pressed) {
        boolean nextValue = enabled && pressed;
        if (this.pressed == nextValue) {
            return false;
        }
        this.pressed = nextValue;
        return true;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            hovered = false;
            pressed = false;
        }
    }

    void setAction(Runnable action) {
        this.action = action == null ? () -> { } : action;
    }

    void runAction() {
        if (enabled) {
            action.run();
        }
    }

    void paint(Graphics2D g2, float scale) {
        double x = bounds.x;
        double y = bounds.y + (pressed ? Math.max(1, scale * 2) : 0);
        double width = bounds.width;
        double height = bounds.height;
        double arc = height;

        g2.setColor(new Color(0, 0, 0, enabled ? 105 : 55));
        g2.fill(new RoundRectangle2D.Double(x + 2, y + 4, width, height, arc, arc));

        Color top;
        Color bottom;
        Color border;
        Color text;
        if (!enabled) {
            top = new Color(35, 43, 46, 150);
            bottom = new Color(22, 28, 31, 150);
            border = new Color(151, 159, 156, 55);
            text = new Color(181, 186, 182, 115);
        } else if (pressed) {
            top = new Color(19, 58, 51);
            bottom = new Color(12, 38, 35);
            border = new Color(246, 205, 104, 205);
            text = new Color(255, 239, 192);
        } else if (hovered) {
            top = new Color(29, 76, 65);
            bottom = new Color(15, 47, 42);
            border = new Color(240, 204, 119, 180);
            text = new Color(255, 242, 206);
        } else {
            top = new Color(23, 48, 47);
            bottom = new Color(12, 31, 33);
            border = new Color(210, 193, 146, 95);
            text = new Color(238, 235, 220);
        }

        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, width, height, arc, arc);
        g2.setPaint(new GradientPaint(0, (float) y, top, 0, (float) (y + height), bottom));
        g2.fill(shape);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(Math.max(1f, 1.2f * scale)));
        g2.draw(shape);

        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, Math.round(13 * scale))));
        g2.setColor(text);
        FontMetrics metrics = g2.getFontMetrics();
        int textX = (int) Math.round(x + (width - metrics.stringWidth(label)) / 2.0);
        int textY = (int) Math.round(y + (height - metrics.getHeight()) / 2.0 + metrics.getAscent());
        g2.drawString(label, textX, textY);
    }
}
