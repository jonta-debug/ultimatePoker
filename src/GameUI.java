import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Legacy Swing frontend, now driven exclusively by Game commands and snapshots. */
public final class GameUI {
    private GameUI() { }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            GameFrame frame = new GameFrame();
            if (frame.isDisplayable()) frame.setVisible(true);
        });
    }

    private static final class GameFrame extends JFrame {
        private Game game;
        private GameSnapshot snapshot;
        private final PokerAssets assets = new PokerAssets();
        private final JLabel potLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel deckCountLabel = new JLabel("", SwingConstants.CENTER);
        private final List<JLabel> communityLabels = new ArrayList<>();
        private final JPanel playerGrid = new JPanel(new GridLayout(0, 3, 16, 16));
        private final JButton nextButton = new JButton("Weiter");
        private List<PlayerPanel> playerPanels = new ArrayList<>();

        private GameFrame() {
            setTitle("Ultimate Poker");
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1320, 860));

            FeltPanel root = new FeltPanel();
            root.setLayout(new BorderLayout(24, 24));
            root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
            setContentPane(root);

            JLabel title = label("Ultimate Poker", 34, Font.BOLD);
            potLabel.setForeground(new Color(248, 221, 142));
            potLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
            JPanel top = transparentBox();
            top.add(title);
            top.add(Box.createVerticalStrut(8));
            top.add(potLabel);
            root.add(top, BorderLayout.NORTH);

            JPanel center = new JPanel(new BorderLayout(28, 0));
            center.setOpaque(false);
            root.add(center, BorderLayout.CENTER);

            JPanel board = transparentBox();
            board.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 233, 221, 45)),
                BorderFactory.createEmptyBorder(26, 28, 26, 28)));
            board.add(label("Table", 18, Font.BOLD));
            board.add(Box.createVerticalStrut(8));

            JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
            cards.setOpaque(false);
            for (int index = 0; index < 5; index++) {
                JLabel card = new JLabel(assets.getPlaceholderIcon());
                communityLabels.add(card);
                cards.add(card);
            }
            board.add(cards);
            statusLabel.setForeground(new Color(241, 232, 206));
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            board.add(statusLabel);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
            controls.setOpaque(false);
            nextButton.addActionListener(event -> advanceRound());
            JButton restart = new JButton("Neues Spiel");
            restart.addActionListener(event -> startNewRound());
            controls.add(nextButton);
            controls.add(restart);
            board.add(controls);
            center.add(board, BorderLayout.CENTER);

            JPanel deckPanel = transparentBox();
            deckPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
            deckPanel.add(label("Deck", 18, Font.BOLD));
            JLabel deck = new JLabel(assets.getDeckIcon());
            deck.setAlignmentX(Component.CENTER_ALIGNMENT);
            deckPanel.add(deck);
            deckCountLabel.setForeground(new Color(245, 239, 218));
            deckCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            deckPanel.add(deckCountLabel);
            center.add(deckPanel, BorderLayout.EAST);

            playerGrid.setOpaque(false);
            JScrollPane scroll = new JScrollPane(playerGrid);
            scroll.setBorder(BorderFactory.createTitledBorder("Players"));
            scroll.getViewport().setOpaque(false);
            scroll.setOpaque(false);
            scroll.setPreferredSize(new Dimension(0, 330));
            root.add(scroll, BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(null);
            startNewRound();
        }

        private JLabel label(String text, int size, int style) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setForeground(new Color(245, 239, 218));
            label.setFont(new Font("SansSerif", style, size));
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            return label;
        }

        private JPanel transparentBox() {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            return panel;
        }

        private void startNewRound() {
            SetupData setup = promptForSetup();
            if (setup == null) {
                dispose();
                return;
            }
            game = new Game();
            game.startRound(setup.playerNames, setup.startingMoney, setup.entryBet);
            snapshot = game.getPublicSnapshot();
            rebuildPlayerPanels();
            refreshView();
        }

        private void advanceRound() {
            switch (game.getStage()) {
                case PREFLOP:
                    game.dealFlop();
                    game.revealFlop();
                    break;
                case FLOP:
                    game.dealTurn();
                    game.revealTurn();
                    break;
                case TURN:
                case RIVER_DEALT:
                case PLAYER_CARDS_REVEALED:
                case RIVER:
                    game.advanceGame();
                    break;
                default:
                    return;
            }
            refreshView();
        }

        private void rebuildPlayerPanels() {
            playerGrid.removeAll();
            playerPanels = new ArrayList<>();
            for (PlayerSnapshot player : snapshot.getPlayers()) {
                PlayerPanel panel = new PlayerPanel(player.getPlayerId(), assets);
                playerPanels.add(panel);
                playerGrid.add(panel);
            }
            playerGrid.revalidate();
            playerGrid.repaint();
        }

        private void refreshView() {
            snapshot = game.getPublicSnapshot();
            potLabel.setText("Pot: " + formatAmount(snapshot.getPot()));
            deckCountLabel.setText(snapshot.getCardsLeftInDeck() + " cards left");
            for (int index = 0; index < communityLabels.size(); index++) {
                JLabel label = communityLabels.get(index);
                if (index < snapshot.getVisibleCommunityCards().size()) {
                    label.setIcon(assets.getCardIcon(snapshot.getVisibleCommunityCards().get(index)));
                } else if (index < snapshot.getDealtCommunityCardCount()) {
                    label.setIcon(assets.getCardBackIcon());
                } else {
                    label.setIcon(assets.getPlaceholderIcon());
                }
            }
            for (PlayerPanel panel : playerPanels) {
                panel.refresh(snapshot.getPlayer(panel.playerId));
            }
            statusLabel.setText(buildStatusText());
            nextButton.setEnabled(!snapshot.getStage().isShowdown());
        }

        private String buildStatusText() {
            if (!snapshot.getStage().isShowdown()) return snapshot.getStage().getStatusText();
            StringBuilder names = new StringBuilder();
            for (int winnerId : snapshot.getWinnerPlayerIds()) {
                if (names.length() > 0) names.append(", ");
                names.append(snapshot.getPlayer(winnerId).getDisplayName());
            }
            return (snapshot.getWinnerPlayerIds().size() == 1 ? "Gewinner: " : "Geteilter Sieg: ")
                + names;
        }

        private SetupData promptForSetup() {
            JSpinner playerCount = new JSpinner(new SpinnerNumberModel(4, 2, 9, 1));
            JSpinner money = new JSpinner(new SpinnerNumberModel(100, 1, 10_000, 5));
            JSpinner ante = new JSpinner(new SpinnerNumberModel(5, 1, 1_000, 1));
            JTextField names = new JTextField("Alice, Bob, Carol, Dave", 20);
            JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
            panel.add(new JLabel("Anzahl Spieler")); panel.add(playerCount);
            panel.add(new JLabel("Startguthaben pro Spieler")); panel.add(money);
            panel.add(new JLabel("Einsatz pro Spieler")); panel.add(ante);
            panel.add(new JLabel("Namen, durch Komma getrennt")); panel.add(names);
            int result = JOptionPane.showConfirmDialog(this, panel, "Spiel starten",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;
            int count = ((Number) playerCount.getValue()).intValue();
            return new SetupData(parseNames(names.getText(), count),
                ((Number) money.getValue()).doubleValue(),
                ((Number) ante.getValue()).doubleValue());
        }

        private List<String> parseNames(String input, int count) {
            List<String> names = new ArrayList<>();
            for (String raw : input.split(",")) {
                if (!raw.trim().isEmpty()) names.add(raw.trim());
            }
            while (names.size() < count) names.add("Player " + (names.size() + 1));
            return names.size() > count ? new ArrayList<>(names.subList(0, count)) : names;
        }
    }

    private static final class PlayerPanel extends JPanel {
        private final int playerId;
        private final PokerAssets assets;
        private final JLabel firstCard = new JLabel();
        private final JLabel secondCard = new JLabel();
        private final JLabel name = new JLabel("", SwingConstants.CENTER);
        private final JLabel money = new JLabel("", SwingConstants.CENTER);
        private final JLabel chips = new JLabel();
        private final JLabel hand = new JLabel(" ", SwingConstants.CENTER);

        private PlayerPanel(int playerId, PokerAssets assets) {
            this.playerId = playerId;
            this.assets = assets;
            setOpaque(true);
            setBackground(new Color(9, 41, 29, 210));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            money.setAlignmentX(Component.CENTER_ALIGNMENT);
            chips.setAlignmentX(Component.CENTER_ALIGNMENT);
            hand.setAlignmentX(Component.CENTER_ALIGNMENT);
            JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            cards.setOpaque(false);
            cards.add(firstCard); cards.add(secondCard);
            add(name); add(money); add(cards); add(chips); add(hand);
        }

        private void refresh(PlayerSnapshot player) {
            name.setText(player.getDisplayName());
            money.setText("Geld: " + formatAmount(player.getStack()));
            firstCard.setIcon(player.getVisibleCards().isEmpty()
                ? assets.getCardBackIcon() : assets.getCardIcon(player.getVisibleCards().get(0)));
            secondCard.setIcon(player.getVisibleCards().size() > 1
                ? assets.getCardIcon(player.getVisibleCards().get(1)) : assets.getCardBackIcon());
            chips.setIcon(assets.getChipStackIcon(player.getCurrentBet()));
            hand.setText(player.getFinalHand() == null
                ? "Eine Karte bleibt verdeckt."
                : (player.isWinner() ? "Winner: " : "") + player.getFinalHand());
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(player.isWinner()
                    ? new Color(248, 211, 92) : new Color(240, 222, 167, 40),
                    player.isWinner() ? 2 : 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        }
    }

    private static String formatAmount(double amount) {
        return amount == Math.rint(amount)
            ? Integer.toString((int) amount) : String.format("%.2f", amount);
    }

    private static final class SetupData {
        private final List<String> playerNames;
        private final double startingMoney;
        private final double entryBet;
        private SetupData(List<String> names, double money, double bet) {
            playerNames = names; startingMoney = money; entryBet = bet;
        }
    }

    private static final class FeltPanel extends JPanel {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, new Color(14, 74, 46),
                0, getHeight(), new Color(5, 37, 30)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }
}
