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

public final class GameUI {
    private GameUI() {
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            GameFrame frame = new GameFrame();
            if (frame.isDisplayable()) {
                frame.setVisible(true);
            }
        });
    }

    private static final class GameFrame extends JFrame {
        private final Game game;
        private final PokerAssets assets;

        private final JLabel potLabel;
        private final JLabel statusLabel;
        private final JLabel deckCountLabel;
        private final JLabel deckLabel;
        private final List<JLabel> communityLabels;
        private final JPanel playerGrid;
        private final JButton nextButton;

        private List<PlayerPanel> playerPanels;
        private int visibleCommunityCards;
        private boolean revealHiddenPlayerCards;
        private boolean revealFinalCommunityCard;
        private Player winner;

        private GameFrame() {
            this.game = new Game();
            this.assets = new PokerAssets();
            this.communityLabels = new ArrayList<>();
            this.playerPanels = new ArrayList<>();

            setTitle("Ultimate Poker");
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1320, 860));

            FeltPanel root = new FeltPanel();
            root.setLayout(new BorderLayout(24, 24));
            root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
            setContentPane(root);

            JLabel titleLabel = new JLabel("Ultimate Poker", SwingConstants.CENTER);
            titleLabel.setForeground(new Color(245, 239, 218));
            titleLabel.setFont(new Font("Serif", Font.BOLD, 34));

            potLabel = new JLabel("", SwingConstants.CENTER);
            potLabel.setForeground(new Color(248, 221, 142));
            potLabel.setFont(new Font("SansSerif", Font.BOLD, 20));

            JPanel topPanel = new JPanel();
            topPanel.setOpaque(false);
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            potLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            topPanel.add(titleLabel);
            topPanel.add(Box.createVerticalStrut(8));
            topPanel.add(potLabel);

            root.add(topPanel, BorderLayout.NORTH);

            JPanel centerPanel = new JPanel(new BorderLayout(28, 0));
            centerPanel.setOpaque(false);
            root.add(centerPanel, BorderLayout.CENTER);

            JPanel boardPanel = new JPanel();
            boardPanel.setOpaque(false);
            boardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 233, 221, 45), 1),
                BorderFactory.createEmptyBorder(26, 28, 26, 28)));
            boardPanel.setLayout(new BoxLayout(boardPanel, BoxLayout.Y_AXIS));

            JLabel boardTitle = new JLabel("Table", SwingConstants.CENTER);
            boardTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
            boardTitle.setForeground(new Color(235, 244, 228));
            boardTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

            JPanel communityPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
            communityPanel.setOpaque(false);
            for (int index = 0; index < 5; index++) {
                JLabel label = new JLabel(assets.getPlaceholderIcon());
                communityLabels.add(label);
                communityPanel.add(label);
            }

            statusLabel = new JLabel("", SwingConstants.CENTER);
            statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            statusLabel.setForeground(new Color(241, 232, 206));
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

            JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
            controlPanel.setOpaque(false);

            nextButton = new JButton("Weiter");
            nextButton.addActionListener(event -> revealNextStep());

            JButton restartButton = new JButton("Neues Spiel");
            restartButton.addActionListener(event -> startNewRound());

            controlPanel.add(nextButton);
            controlPanel.add(restartButton);

            boardPanel.add(boardTitle);
            boardPanel.add(Box.createVerticalStrut(8));
            boardPanel.add(communityPanel);
            boardPanel.add(Box.createVerticalStrut(10));
            boardPanel.add(statusLabel);
            boardPanel.add(Box.createVerticalStrut(10));
            boardPanel.add(controlPanel);
            centerPanel.add(boardPanel, BorderLayout.CENTER);

            JPanel deckPanel = new JPanel();
            deckPanel.setOpaque(false);
            deckPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 233, 221, 45), 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
            deckPanel.setLayout(new BoxLayout(deckPanel, BoxLayout.Y_AXIS));

            JLabel deckTitle = new JLabel("Deck", SwingConstants.CENTER);
            deckTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
            deckTitle.setForeground(new Color(235, 244, 228));
            deckTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

            deckLabel = new JLabel(assets.getDeckIcon());
            deckLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            deckCountLabel = new JLabel("", SwingConstants.CENTER);
            deckCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            deckCountLabel.setForeground(new Color(245, 239, 218));
            deckCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

            deckPanel.add(deckTitle);
            deckPanel.add(Box.createVerticalStrut(16));
            deckPanel.add(deckLabel);
            deckPanel.add(Box.createVerticalStrut(12));
            deckPanel.add(deckCountLabel);
            centerPanel.add(deckPanel, BorderLayout.EAST);

            playerGrid = new JPanel(new GridLayout(0, 3, 16, 16));
            playerGrid.setOpaque(false);

            JScrollPane playerScrollPane = new JScrollPane(playerGrid);
            playerScrollPane.setBorder(BorderFactory.createTitledBorder("Players"));
            playerScrollPane.getViewport().setOpaque(false);
            playerScrollPane.setOpaque(false);
            playerScrollPane.setPreferredSize(new Dimension(0, 330));
            root.add(playerScrollPane, BorderLayout.SOUTH);

            pack();
            setLocationRelativeTo(null);
            startNewRound();
        }

        private void startNewRound() {
            SetupData setupData = promptForSetup();
            if (setupData == null) {
                dispose();
                return;
            }

            game.setupRound(setupData.playerNames, setupData.startingMoney, setupData.entryBet);
            visibleCommunityCards = 0;
            revealHiddenPlayerCards = false;
            revealFinalCommunityCard = false;
            winner = null;

            rebuildPlayerPanels();
            refreshView();
        }

        private SetupData promptForSetup() {
            JSpinner playerCountSpinner = new JSpinner(new SpinnerNumberModel(4, 2, 9, 1));
            JSpinner moneySpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10_000, 5));
            JSpinner anteSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 1_000, 1));
            JTextField nameField = new JTextField("Alice, Bob, Carol, Dave", 20);

            JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
            panel.add(new JLabel("Anzahl Spieler"));
            panel.add(playerCountSpinner);
            panel.add(new JLabel("Startguthaben pro Spieler"));
            panel.add(moneySpinner);
            panel.add(new JLabel("Einsatz pro Spieler"));
            panel.add(anteSpinner);
            panel.add(new JLabel("Namen, durch Komma getrennt"));
            panel.add(nameField);

            int result = JOptionPane.showConfirmDialog(this, panel, "Spiel starten", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            int playerCount = ((Number) playerCountSpinner.getValue()).intValue();
            double startingMoney = ((Number) moneySpinner.getValue()).doubleValue();
            double entryBet = ((Number) anteSpinner.getValue()).doubleValue();

            List<String> names = parseNames(nameField.getText(), playerCount);
            return new SetupData(names, startingMoney, entryBet);
        }

        private List<String> parseNames(String input, int playerCount) {
            String[] rawNames = input.split(",");
            List<String> names = new ArrayList<>();

            for (String rawName : rawNames) {
                String trimmed = rawName.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }

            while (names.size() < playerCount) {
                names.add("Player " + (names.size() + 1));
            }

            if (names.size() > playerCount) {
                return new ArrayList<>(names.subList(0, playerCount));
            }

            return names;
        }

        private void rebuildPlayerPanels() {
            playerGrid.removeAll();
            playerPanels = new ArrayList<>();

            for (Player player : game.getPlayers()) {
                PlayerPanel panel = new PlayerPanel(player, assets);
                playerPanels.add(panel);
                playerGrid.add(panel);
            }

            playerGrid.revalidate();
            playerGrid.repaint();
        }

        private void revealNextStep() {
            if (visibleCommunityCards < 4) {
                visibleCommunityCards++;
            } else if (!revealHiddenPlayerCards) {
                revealHiddenPlayerCards = true;
            } else if (!revealFinalCommunityCard) {
                revealFinalCommunityCard = true;
                winner = game.determineWinner();
            } else {
                nextButton.setEnabled(false);
            }

            refreshView();
        }

        private void refreshView() {
            potLabel.setText("Pot: " + formatAmount(game.getPot()));
            deckCountLabel.setText(game.getCardsLeftInDeck() + " cards left");

            for (int index = 0; index < communityLabels.size(); index++) {
                JLabel label = communityLabels.get(index);
                if (index < visibleCommunityCards) {
                    label.setIcon(assets.getCardIcon(game.getCommunityCards().get(index)));
                } else if (index == 4 && visibleCommunityCards >= 4 && !revealFinalCommunityCard) {
                    label.setIcon(assets.getCardBackIcon());
                } else if (index == 4 && revealFinalCommunityCard) {
                    label.setIcon(assets.getCardIcon(game.getCommunityCards().get(index)));
                } else {
                    label.setIcon(assets.getPlaceholderIcon());
                }
            }

            for (PlayerPanel panel : playerPanels) {
                boolean isWinner = revealFinalCommunityCard && panel.player == winner;
                HandResult result = revealFinalCommunityCard ? game.evaluatePlayer(panel.player) : null;
                panel.refresh(revealHiddenPlayerCards || revealFinalCommunityCard, isWinner, result);
            }

            statusLabel.setText(buildStatusText());
            nextButton.setEnabled(!revealFinalCommunityCard || winner == null);
            if (revealFinalCommunityCard && winner != null) {
                nextButton.setEnabled(false);
            }
        }

        private String buildStatusText() {
            if (visibleCommunityCards < 4) {
                return "Offene Tischkarte " + (visibleCommunityCards + 1) + " wird als Nächstes aufgedeckt.";
            }
            if (!revealHiddenPlayerCards) {
                return "Die vierte Tischkarte ist offen. Als Nächstes werden die verdeckten Handkarten aufgedeckt.";
            }
            if (!revealFinalCommunityCard) {
                return "Alle Spieler haben jetzt beide Handkarten offen. Als Nächstes kommt die letzte Tischkarte.";
            }

            HandResult winnerResult = game.evaluatePlayer(winner);
            return "Gewinner: " + winner.getName() + " mit " + winnerResult;
        }

        private String formatAmount(double amount) {
            if (amount == Math.rint(amount)) {
                return Integer.toString((int) amount);
            }
            return String.format("%.2f", amount);
        }
    }

    private static final class PlayerPanel extends JPanel {
        private final Player player;
        private final PokerAssets assets;
        private final JLabel firstCardLabel;
        private final JLabel secondCardLabel;
        private final JLabel chipLabel;
        private final JLabel nameLabel;
        private final JLabel moneyLabel;
        private final JLabel handLabel;

        private PlayerPanel(Player player, PokerAssets assets) {
            this.player = player;
            this.assets = assets;

            setOpaque(true);
            setBackground(new Color(9, 41, 29, 210));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 222, 167, 40), 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            nameLabel = new JLabel(player.getName(), SwingConstants.CENTER);
            nameLabel.setForeground(new Color(247, 241, 222));
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            moneyLabel = new JLabel("Geld: " + formatAmount(player.getMoneyAmount()), SwingConstants.CENTER);
            moneyLabel.setForeground(new Color(199, 223, 206));
            moneyLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
            moneyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            JPanel cardPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            cardPanel.setOpaque(false);
            firstCardLabel = new JLabel();
            secondCardLabel = new JLabel();
            cardPanel.add(firstCardLabel);
            cardPanel.add(secondCardLabel);

            chipLabel = new JLabel();
            chipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            handLabel = new JLabel(" ", SwingConstants.CENTER);
            handLabel.setForeground(new Color(247, 211, 121));
            handLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            handLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            add(nameLabel);
            add(Box.createVerticalStrut(4));
            add(moneyLabel);
            add(Box.createVerticalStrut(8));
            add(cardPanel);
            add(Box.createVerticalStrut(8));
            add(chipLabel);
            add(Box.createVerticalStrut(8));
            add(handLabel);
        }

        private void refresh(boolean revealSecondCard, boolean isWinner, HandResult result) {
            firstCardLabel.setIcon(assets.getCardIcon(player.getHand().get(0)));
            secondCardLabel.setIcon(revealSecondCard ? assets.getCardIcon(player.getHand().get(1)) : assets.getCardBackIcon());
            chipLabel.setIcon(assets.getChipStackIcon(player.getCurrentBet()));

            if (result == null) {
                handLabel.setText("Eine Karte bleibt verdeckt.");
            } else if (isWinner) {
                handLabel.setText("Winner: " + result);
            } else {
                handLabel.setText(result.toString());
            }

            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isWinner ? new Color(248, 211, 92) : new Color(240, 222, 167, 40), isWinner ? 2 : 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        }

        private String formatAmount(double amount) {
            if (amount == Math.rint(amount)) {
                return Integer.toString((int) amount);
            }
            return String.format("%.2f", amount);
        }
    }

    private static final class SetupData {
        private final List<String> playerNames;
        private final double startingMoney;
        private final double entryBet;

        private SetupData(List<String> playerNames, double startingMoney, double entryBet) {
            this.playerNames = playerNames;
            this.startingMoney = startingMoney;
            this.entryBet = entryBet;
        }
    }

    private static final class FeltPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint gradient = new GradientPaint(0, 0, new Color(14, 74, 46), 0, getHeight(), new Color(5, 37, 30));
            graphics2d.setPaint(gradient);
            graphics2d.fillRect(0, 0, getWidth(), getHeight());

            graphics2d.setColor(new Color(255, 236, 177, 18));
            graphics2d.fillOval(getWidth() / 2 - 280, 80, 560, 260);

            graphics2d.setColor(new Color(255, 255, 255, 9));
            for (int offset = -60; offset < getWidth(); offset += 110) {
                graphics2d.drawLine(offset, 0, offset + 180, getHeight());
            }

            graphics2d.dispose();
        }
    }
}
