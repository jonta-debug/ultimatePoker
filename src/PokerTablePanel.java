import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Game-table canvas. It renders a TableViewState when one is supplied and keeps
 * its original placeholder mode when the state is null.
 */
public class PokerTablePanel extends JPanel {
    private static final Color TEXT = new Color(245, 239, 218);
    private static final int BADGE_WIDTH = 156;
    private static final int BADGE_HEIGHT = 66;
    private static final int COMMUNITY_CARD_WIDTH = 69;
    private static final int COMMUNITY_CARD_HEIGHT = 93;
    private static final int PLAYER_CARD_WIDTH = 46;
    private static final int PLAYER_CARD_HEIGHT = 62;

    private final PokerAssets assets;
    private final TableControlButton nextButton;
    private final TableControlButton newRoundButton;
    private final TableControlButton backButton;
    private final List<TableControlButton> controlButtons;
    private int playerCount;
    private double pot;
    private TableViewState viewState;
    private TableControlButton pressedButton;
    private AnimationManager animationManager;

    public PokerTablePanel(int playerCount) {
        this(playerCount, loadAssetsSafely());
    }

    PokerTablePanel(int playerCount, PokerAssets assets) {
        this.assets = assets;
        this.nextButton = new TableControlButton("Next");
        this.newRoundButton = new TableControlButton("New Round");
        this.backButton = new TableControlButton("Back");
        this.controlButtons = Arrays.asList(nextButton, newRoundButton, backButton);
        setOpaque(true);
        setPreferredSize(new Dimension(1280, 800));
        setPlayerCount(playerCount);
        installControlMouseHandling();
    }

    private static PokerAssets loadAssetsSafely() {
        try {
            return new PokerAssets();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public final void setPlayerCount(int playerCount) {
        SeatLayout.forPlayerCount(playerCount); // validates the range
        this.playerCount = playerCount;
        repaint();
    }

    public void setPot(double pot) {
        this.pot = pot;
        repaint();
    }

    public void setViewState(TableViewState viewState) {
        if (viewState != null) {
            SeatLayout.forPlayerCount(viewState.getPlayers().size());
        }
        this.viewState = viewState;
        repaint();
    }

    public void setAnimationManager(AnimationManager animationManager) {
        if (this.animationManager != null && this.animationManager != animationManager) {
            this.animationManager.clear();
        }
        this.animationManager = animationManager;
        repaint();
    }

    public Rectangle getDeckCardBounds() {
        return new Rectangle(
            (int) (getWidth() * 0.70),
            (int) (getHeight() * 0.46),
            74,
            100);
    }

    public Rectangle getCommunityCardBounds(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= 5) {
            throw new IllegalArgumentException("Community card index must be between 0 and 4");
        }
        int gap = 11;
        int totalWidth = COMMUNITY_CARD_WIDTH * 5 + gap * 4;
        int startX = (getWidth() - totalWidth) / 2;
        return new Rectangle(
            startX + cardIndex * (COMMUNITY_CARD_WIDTH + gap),
            (int) (getHeight() * 0.47),
            COMMUNITY_CARD_WIDTH,
            COMMUNITY_CARD_HEIGHT);
    }

    public Rectangle getPlayerCardBounds(int playerIndex, int cardIndex) {
        int displayedPlayerCount = viewState == null ? playerCount : viewState.getPlayers().size();
        if (playerIndex < 0 || playerIndex >= displayedPlayerCount) {
            throw new IllegalArgumentException("Player index is outside the current table");
        }
        if (cardIndex < 0 || cardIndex >= 2) {
            throw new IllegalArgumentException("Player card index must be 0 or 1");
        }

        SeatLayout.Seat seat = SeatLayout.forPlayerCount(displayedPlayerCount).get(playerIndex);
        Point anchor = seat.anchor(getWidth(), getHeight());
        return new Rectangle(
            anchor.x + seat.getCardOffsetX() + cardIndex * (PLAYER_CARD_WIDTH + 6),
            anchor.y + seat.getCardOffsetY(),
            PLAYER_CARD_WIDTH,
            PLAYER_CARD_HEIGHT);
    }

    public Rectangle getPlayerBadgeBounds(int playerIndex) {
        int displayedPlayerCount = viewState == null ? playerCount : viewState.getPlayers().size();
        if (playerIndex < 0 || playerIndex >= displayedPlayerCount) {
            throw new IllegalArgumentException("Player index is outside the current table");
        }
        SeatLayout.Seat seat = SeatLayout.forPlayerCount(displayedPlayerCount).get(playerIndex);
        Point anchor = seat.anchor(getWidth(), getHeight());
        return new Rectangle(
            anchor.x - BADGE_WIDTH / 2,
            anchor.y - BADGE_HEIGHT / 2,
            BADGE_WIDTH,
            BADGE_HEIGHT);
    }

    public BufferedImage getCardImageForAnimation(Card card, int width, int height) {
        return scaledCardImage(card, width, height);
    }

    public BufferedImage getCardBackImageForAnimation(int width, int height) {
        return scaledCardBack(width, height);
    }

    public void setNextAction(Runnable action) {
        nextButton.setAction(action);
    }

    public void setNewRoundAction(Runnable action) {
        newRoundButton.setAction(action);
    }

    public void setBackAction(Runnable action) {
        backButton.setAction(action);
    }

    public void setNextEnabled(boolean enabled) {
        nextButton.setEnabled(enabled);
        repaint();
    }

    public void setNewRoundEnabled(boolean enabled) {
        newRoundButton.setEnabled(enabled);
        repaint();
    }

    public void setBackEnabled(boolean enabled) {
        backButton.setEnabled(enabled);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            applyQualityHints(g2);
            layoutControlButtons();
            drawBackground(g2);
            drawTable(g2);
            drawPot(g2);
            drawCommunityCards(g2);
            drawDeckPlaceholder(g2);
            if (viewState == null) {
                drawSeatPlaceholders(g2);
            } else {
                drawPlayers(g2);
                drawStatus(g2);
            }
            drawControls(g2);
            if (animationManager != null) {
                animationManager.draw(g2);
            }
        } finally {
            g2.dispose();
        }
    }

    private void applyQualityHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private void installControlMouseHandling() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateControlHover(event.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                updateControlHover(event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                clearControlHover();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }

                layoutControlButtons();
                pressedButton = findControlAt(event.getPoint());
                if (pressedButton != null && pressedButton.setPressed(true)) {
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }

                TableControlButton releasedButton = pressedButton;
                pressedButton = null;
                boolean shouldRun = releasedButton != null && releasedButton.contains(event.getPoint());
                if (releasedButton != null) {
                    releasedButton.setPressed(false);
                }
                updateControlHover(event.getPoint());
                repaint();

                if (shouldRun) {
                    releasedButton.runAction();
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    private void layoutControlButtons() {
        float scale = controlScale();
        double gap = 12 * scale;
        double height = 38 * scale;
        double[] widths = {90 * scale, 116 * scale, 90 * scale};
        double totalWidth = widths[0] + widths[1] + widths[2] + gap * 2;
        double x = (getWidth() - totalWidth) / 2.0;

        // Keep the controls between the winner text and bottom-seat cards.
        double bottomCardTop = getHeight() * 0.91 - 116;
        double y = Math.min(getHeight() - height - 12, bottomCardTop - height - 10);
        y = Math.max(getHeight() * 0.54, y);

        for (int index = 0; index < controlButtons.size(); index++) {
            controlButtons.get(index).setBounds(x, y, widths[index], height);
            x += widths[index] + gap;
        }
    }

    private float controlScale() {
        double widthScale = getWidth() / 1280.0;
        double heightScale = getHeight() / 800.0;
        return (float) Math.max(0.78, Math.min(1.10, Math.min(widthScale, heightScale)));
    }

    private void drawControls(Graphics2D g2) {
        float scale = controlScale();
        for (TableControlButton button : controlButtons) {
            button.paint(g2, scale);
        }
    }

    private TableControlButton findControlAt(Point point) {
        layoutControlButtons();
        for (TableControlButton button : controlButtons) {
            if (button.contains(point)) {
                return button;
            }
        }
        return null;
    }

    private void updateControlHover(Point point) {
        layoutControlButtons();
        boolean changed = false;
        boolean overControl = false;
        for (TableControlButton button : controlButtons) {
            boolean contains = button.contains(point);
            changed |= button.setHovered(contains);
            overControl |= contains;
        }
        setCursor(Cursor.getPredefinedCursor(overControl
            ? Cursor.HAND_CURSOR
            : Cursor.DEFAULT_CURSOR));
        if (changed) {
            repaint();
        }
    }

    private void clearControlHover() {
        boolean changed = false;
        for (TableControlButton button : controlButtons) {
            changed |= button.setHovered(false);
        }
        setCursor(Cursor.getDefaultCursor());
        if (changed) {
            repaint();
        }
    }

    private void drawBackground(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0, 0, new Color(12, 17, 22),
            0, getHeight(), new Color(3, 7, 10)));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawTable(Graphics2D g2) {
        double x = getWidth() * 0.14;
        double y = getHeight() * 0.18;
        double width = getWidth() * 0.72;
        double height = getHeight() * 0.64;

        Ellipse2D shadow = new Ellipse2D.Double(x - 12, y + 15, width + 24, height + 22);
        g2.setColor(new Color(0, 0, 0, 125));
        g2.fill(shadow);

        Ellipse2D rail = new Ellipse2D.Double(x - 11, y - 11, width + 22, height + 22);
        g2.setPaint(new GradientPaint(0, (float) y, new Color(88, 57, 34),
            0, (float) (y + height), new Color(38, 24, 17)));
        g2.fill(rail);

        Ellipse2D felt = new Ellipse2D.Double(x, y, width, height);
        g2.setPaint(new GradientPaint(0, (float) y, new Color(23, 108, 73),
            0, (float) (y + height), new Color(8, 57, 42)));
        g2.fill(felt);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(221, 187, 116, 85));
        g2.draw(felt);
    }

    private void drawPot(Graphics2D g2) {
        double displayedPot = viewState == null ? pot : viewState.getPot();
        String text = "POT  " + formatAmount(displayedPot);
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(new Color(250, 218, 133));
        drawCenteredString(g2, text, getWidth() / 2, (int) (getHeight() * 0.42));
    }

    private void drawCommunityCards(Graphics2D g2) {
        for (int index = 0; index < 5; index++) {
            Rectangle slot = getCommunityCardBounds(index);
            boolean slotAnimating = animationManager != null
                && animationManager.isSlotAnimating(AnimationSlot.communityCard(index));
            if (slotAnimating) {
                drawCardPlaceholder(g2, slot.x, slot.y, slot.width, slot.height);
            } else if (viewState != null
                    && index < viewState.getRevealedCommunityCardCount()) {
                drawCardFace(g2, viewState.getCommunityCards().get(index),
                    slot.x, slot.y, slot.width, slot.height);
            } else if (viewState != null && viewState.isCommunityCardFaceDown(index)) {
                drawCardBack(g2, slot.x, slot.y, slot.width, slot.height);
            } else {
                drawCardPlaceholder(g2, slot.x, slot.y, slot.width, slot.height);
            }
        }
    }

    private void drawCardPlaceholder(Graphics2D g2, int x, int y, int width, int height) {
        g2.setColor(new Color(0, 0, 0, 65));
        g2.fill(new RoundRectangle2D.Double(x + 4, y + 6, width, height, 12, 12));

        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, width, height, 12, 12);
        g2.setColor(new Color(236, 244, 238, 28));
        g2.fill(card);
        g2.setColor(new Color(236, 244, 238, 95));
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(card);
    }

    private void drawDeckPlaceholder(Graphics2D g2) {
        Rectangle bounds = getDeckCardBounds();
        int width = bounds.width;
        int height = bounds.height;
        int x = bounds.x;
        int y = bounds.y;
        BufferedImage cardBack = scaledCardBack(width, height);

        g2.setColor(new Color(0, 0, 0, 80));
        g2.fill(new RoundRectangle2D.Double(x + 7, y + 8, width, height, 12, 12));

        for (int layer = 2; layer >= 0; layer--) {
            int offset = layer * 3;
            if (cardBack != null) {
                g2.drawImage(cardBack, x + offset, y - offset, null);
            } else {
                RoundRectangle2D card = new RoundRectangle2D.Double(
                    x + offset, y - offset, width, height, 12, 12);
                g2.setColor(new Color(15, 31, 48));
                g2.fill(card);
                g2.setColor(new Color(225, 235, 230, 125));
                g2.draw(card);
            }
        }

        String deckText = viewState == null
            ? "DECK"
            : Integer.toString(viewState.getCardsLeftInDeck());
        int labelWidth = viewState == null ? 52 : 38;
        g2.setColor(new Color(4, 10, 14, 185));
        g2.fill(new RoundRectangle2D.Double(
            x + (width - labelWidth) / 2.0 + 3, y + height / 2.0 - 17,
            labelWidth, 29, 15, 15));
        g2.setFont(new Font("SansSerif", Font.BOLD, viewState == null ? 12 : 22));
        g2.setColor(TEXT);
        drawCenteredString(g2, deckText, x + width / 2 + 3, y + height / 2 + 4);

        if (viewState != null) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(new Color(220, 231, 225));
            drawCenteredString(g2, "CARDS LEFT", x + width / 2 + 3, y + height + 19);
        }
    }

    private void drawPlayers(Graphics2D g2) {
        List<PlayerView> players = viewState.getPlayers();

        for (int index = 0; index < players.size(); index++) {
            PlayerView player = players.get(index);
            drawPlayerCards(g2, player, index);
            drawPlayerBadge(g2, player, index);
        }
    }

    private void drawPlayerCards(Graphics2D g2, PlayerView player, int playerIndex) {
        for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
            Rectangle slot = getPlayerCardBounds(playerIndex, cardIndex);
            boolean slotAnimating = animationManager != null
                && animationManager.isSlotAnimating(
                    AnimationSlot.playerCard(playerIndex, cardIndex));
            if (slotAnimating) {
                continue;
            }
            if (cardIndex < player.getCards().size()) {
                drawCardFace(g2, player.getCards().get(cardIndex),
                    slot.x, slot.y, slot.width, slot.height);
            } else if (cardIndex < player.getCards().size() + player.getHiddenCardCount()) {
                drawCardBack(g2, slot.x, slot.y, slot.width, slot.height);
            } else {
                drawCardPlaceholder(g2, slot.x, slot.y, slot.width, slot.height);
            }
        }
    }

    private void drawPlayerBadge(Graphics2D g2, PlayerView player, int playerIndex) {
        Rectangle bounds = getPlayerBadgeBounds(playerIndex);
        int x = bounds.x;
        int y = bounds.y;
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;

        if (player.isWinner()) {
            g2.setColor(new Color(250, 207, 82, 55));
            g2.fill(new RoundRectangle2D.Double(
                x - 7, y - 7, BADGE_WIDTH + 14, BADGE_HEIGHT + 14, 28, 28));
        }

        g2.setColor(new Color(0, 0, 0, 115));
        g2.fill(new RoundRectangle2D.Double(
            x + 3, y + 5, BADGE_WIDTH, BADGE_HEIGHT, 22, 22));

        RoundRectangle2D badge = new RoundRectangle2D.Double(
            x, y, BADGE_WIDTH, BADGE_HEIGHT, 22, 22);
        g2.setColor(new Color(12, 29, 35, 242));
        g2.fill(badge);
        g2.setColor(player.isWinner()
            ? new Color(250, 211, 92)
            : new Color(217, 190, 125, 110));
        g2.setStroke(new BasicStroke(player.isWinner() ? 2.2f : 1.2f));
        g2.draw(badge);

        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(TEXT);
        drawCenteredString(g2, player.getName(), centerX, centerY - 10);

        String moneyAndBet = "Money " + formatAmount(player.getMoney())
            + "  |  Bet " + formatAmount(player.getBet());
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(197, 220, 207));
        drawCenteredString(g2, moneyAndBet, centerX, centerY + 8);

        if (!player.getStatusText().isEmpty()) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.setColor(player.isWinner()
                ? new Color(250, 218, 126)
                : new Color(206, 210, 204));
            drawCenteredString(g2, player.getStatusText(), centerX, centerY + 24);
        }
    }

    private void drawCardFace(Graphics2D g2, Card card, int x, int y, int width, int height) {
        BufferedImage image = scaledCardImage(card, width, height);
        if (image == null) {
            drawTextCardFallback(g2, card, x, y, width, height);
            return;
        }

        g2.setColor(new Color(0, 0, 0, 75));
        g2.fill(new RoundRectangle2D.Double(x + 4, y + 5, width, height, 10, 10));
        g2.drawImage(image, x, y, null);
    }

    private void drawTextCardFallback(Graphics2D g2, Card card, int x, int y, int width, int height) {
        g2.setColor(new Color(0, 0, 0, 75));
        g2.fill(new RoundRectangle2D.Double(x + 4, y + 5, width, height, 10, 10));

        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, width, height, 10, 10);
        g2.setColor(new Color(246, 244, 235));
        g2.fill(shape);
        g2.setColor(new Color(205, 201, 188));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(shape);

        g2.setFont(new Font("SansSerif", Font.BOLD, width >= 60 ? 19 : 14));
        g2.setColor(isRedSuit(card.getSuit())
            ? new Color(181, 43, 48)
            : new Color(25, 31, 36));
        drawCenteredString(g2, cardLabel(card), x + width / 2, y + height / 2 + 6);
    }

    private void drawCardBack(Graphics2D g2, int x, int y, int width, int height) {
        BufferedImage image = scaledCardBack(width, height);
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fill(new RoundRectangle2D.Double(x + 4, y + 5, width, height, 10, 10));

        if (image != null) {
            g2.drawImage(image, x, y, null);
            return;
        }

        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, width, height, 10, 10);
        g2.setColor(new Color(23, 52, 76));
        g2.fill(shape);
        g2.setColor(new Color(220, 228, 222, 150));
        g2.draw(shape);
    }

    private BufferedImage scaledCardImage(Card card, int width, int height) {
        if (assets == null) {
            return null;
        }
        try {
            return assets.getScaledCardImage(card, width, height);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BufferedImage scaledCardBack(int width, int height) {
        if (assets == null) {
            return null;
        }
        try {
            return assets.getScaledCardBackImage(width, height);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void drawStatus(Graphics2D g2) {
        String status = viewState.getStatusText();
        if (status == null || status.isEmpty()) {
            return;
        }

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(viewState.getWinnerPlayerId() >= 0
            ? new Color(250, 218, 126)
            : TEXT);
        int baselineY = (int) Math.round(getHeight() * 0.35);
        drawCenteredString(g2, status, getWidth() / 2, baselineY);
    }

    private String cardLabel(Card card) {
        String rank;
        switch (card.getRank()) {
            case ACE:
                rank = "A";
                break;
            case KING:
                rank = "K";
                break;
            case QUEEN:
                rank = "Q";
                break;
            case JACK:
                rank = "J";
                break;
            case TEN:
                rank = "10";
                break;
            default:
                rank = Integer.toString(card.getRank().getValue());
                break;
        }

        return rank + suitSymbol(card.getSuit());
    }

    private String suitSymbol(Suit suit) {
        switch (suit) {
            case HEARTS:
                return "\u2665";
            case DIAMONDS:
                return "\u2666";
            case CLUBS:
                return "\u2663";
            case SPADES:
                return "\u2660";
            default:
                return "";
        }
    }

    private boolean isRedSuit(Suit suit) {
        return suit == Suit.HEARTS || suit == Suit.DIAMONDS;
    }

    private void drawSeatPlaceholders(Graphics2D g2) {
        List<SeatLayout.Seat> seats = SeatLayout.forPlayerCount(playerCount);
        for (int index = 0; index < seats.size(); index++) {
            Point anchor = seats.get(index).anchor(getWidth(), getHeight());
            int x = anchor.x - BADGE_WIDTH / 2;
            int y = anchor.y - BADGE_HEIGHT / 2;

            g2.setColor(new Color(0, 0, 0, 105));
            g2.fill(new RoundRectangle2D.Double(
                x + 3, y + 5, BADGE_WIDTH, BADGE_HEIGHT, 22, 22));

            RoundRectangle2D badge = new RoundRectangle2D.Double(
                x, y, BADGE_WIDTH, BADGE_HEIGHT, 22, 22);
            g2.setColor(new Color(12, 29, 35, 235));
            g2.fill(badge);
            g2.setColor(new Color(217, 190, 125, 110));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(badge);

            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.setColor(TEXT);
            drawCenteredString(g2, "Seat " + (index + 1), anchor.x, anchor.y + 5);
        }
    }

    private void drawCenteredString(Graphics2D g2, String text, int centerX, int baselineY) {
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    private String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return Integer.toString((int) amount);
        }
        return String.format("%.2f", amount);
    }
}
